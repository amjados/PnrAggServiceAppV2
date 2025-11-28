package com.pnr.aggregator.service;

import com.pnr.aggregator.model.entity.Baggage;
import com.pnr.aggregator.model.entity.BaggageAllowance;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * @Service: Marks this as a Spring service component
 * - Registers this class as a bean in the Spring context
 * - Contains business logic for baggage data retrieval
 */
@Service
/**
 * @Slf4j: Lombok annotation for logger generation
 * - Creates: private static final Logger log = LoggerFactory.getLogger(BaggageService.class)
 */
@Slf4j
public class BaggageService {

    /**
     * @Autowired: Dependency injection for MongoClient
     * - Injects Vert.x MongoClient for async database operations
     */
    @Autowired
    private MongoClient mongoClient;

    /**
     * @Autowired: Dependency injection for CacheManager
     * - Injects Redis-based cache manager for fallback data
     */
    @Autowired
    private CacheManager cacheManager;

    /**
     * @Autowired: Dependency injection for CircuitBreakerRegistry
     * - Manages circuit breaker instances for resilience patterns
     */
    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    private CircuitBreaker circuitBreaker;

    /**
     * @PostConstruct: Post-initialization lifecycle hook
     * - Executes after all dependencies are injected
     * - Initializes the circuit breaker from the registry
     */
    @jakarta.annotation.PostConstruct
    public void init() {
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker("baggageServiceCB");
        log.info("BaggageService Circuit Breaker initialized: {}", circuitBreaker.getName());
    }

    public Future<Baggage> getBaggageInfo(String pnr) {
        log.info("[CB-BEFORE] BaggageService call for PNR: {} | State: {}", pnr, circuitBreaker.getState());

        // Check if circuit is open
        if (!circuitBreaker.tryAcquirePermission()) {
            log.warn("[CB-REJECTED] Circuit is OPEN - calling fallback for PNR: {}", pnr);
            // Don't call onError() here - rejection is already tracked by CB
            return getBaggageFallback(pnr, new Exception("Circuit breaker is OPEN"));
        }

        long start = System.nanoTime();
        Promise<Baggage> promise = Promise.promise();

        // NoSQL Injection Prevention: Parameterized query with validated input
        JsonObject query = new JsonObject().put("bookingReference", pnr);

        mongoClient.findOne("baggage", query, null, ar -> {
            long duration = System.nanoTime() - start;

            if (ar.succeeded()) {
                if (ar.result() == null) {
                    log.warn("Baggage not found for PNR: {}", pnr);
                    circuitBreaker.onError(duration, java.util.concurrent.TimeUnit.NANOSECONDS,
                            new RuntimeException("Baggage not found"));

                    // Use fallback
                    getBaggageFallback(pnr, new Exception("Baggage not found")).onComplete(fallbackResult -> {
                        if (fallbackResult.succeeded()) {
                            promise.complete(fallbackResult.result());
                        } else {
                            promise.fail(fallbackResult.cause());
                        }
                    });
                } else {
                    Baggage baggage = mapToBaggage(ar.result());
                    baggage.setFromCache(false);
                    baggage.setFromDefault(false);

                    // Manually cache for fallback
                    Cache cache = cacheManager.getCache("baggage");
                    if (cache != null) {
                        cache.put(pnr, baggage);
                        log.debug("Cached baggage data for PNR: {}", pnr);
                    }

                    circuitBreaker.onSuccess(duration, java.util.concurrent.TimeUnit.NANOSECONDS);
                    promise.complete(baggage);
                    log.info("Baggage fetched successfully for PNR: {}", pnr);
                }
            } else {
                log.error("MongoDB error fetching baggage for PNR: {}", pnr, ar.cause());
                circuitBreaker.onError(duration, java.util.concurrent.TimeUnit.NANOSECONDS, ar.cause());

                // Use fallback
                getBaggageFallback(pnr, new Exception(ar.cause())).onComplete(fallbackResult -> {
                    if (fallbackResult.succeeded()) {
                        promise.complete(fallbackResult.result());
                    } else {
                        promise.fail(fallbackResult.cause());
                    }
                });
            }
        });

        return promise.future();
    }

    /**
     * Fallback: Return default economy baggage allowance
     * 
     * When MongoDB is unavailable, return standard allowance
     * This allows booking to proceed with reasonable defaults
     * Sets baggageFallbackMsg to indicate default usage
     */
    private Future<Baggage> getBaggageFallback(String pnr, Exception ex) {
        log.warn("Circuit OPEN for BaggageService - using default allowance for PNR: {}", pnr);

        // Return default economy baggage allowance
        Baggage defaultBaggage = new Baggage();
        defaultBaggage.setBookingReference(pnr);
        defaultBaggage.setFromCache(false);
        defaultBaggage.setFromDefault(true);

        BaggageAllowance defaultAllowance = new BaggageAllowance();
        defaultAllowance.setAllowanceUnit("kg");
        defaultAllowance.setCheckedAllowanceValue(25); // Standard economy
        defaultAllowance.setCarryOnAllowanceValue(7);

        List<BaggageAllowance> allowances = new ArrayList<>();
        allowances.add(defaultAllowance);
        defaultBaggage.setAllowances(allowances);

        // Set fallback messages for baggage data
        defaultBaggage.setBaggageFallbackMsg(List.of(
                "Using default baggage allowance - service unavailable",
                "Default: 25kg checked, 7kg carry-on"));

        log.info("Returning default baggage allowance for PNR: {}", pnr);
        return Future.succeededFuture(defaultBaggage);
    }

    private Baggage mapToBaggage(JsonObject doc) {
        Baggage baggage = new Baggage();
        baggage.setBookingReference(doc.getString("bookingReference"));

        JsonArray allowancesArray = doc.getJsonArray("baggageAllowances");
        List<BaggageAllowance> allowances = new ArrayList<>();

        for (int i = 0; i < allowancesArray.size(); i++) {
            JsonObject a = allowancesArray.getJsonObject(i);
            BaggageAllowance allowance = new BaggageAllowance();
            allowance.setPassengerNumber(a.getInteger("passengerNumber"));
            allowance.setAllowanceUnit(a.getString("allowanceUnit"));
            allowance.setCheckedAllowanceValue(a.getInteger("checkedAllowanceValue"));
            allowance.setCarryOnAllowanceValue(a.getInteger("carryOnAllowanceValue"));
            allowances.add(allowance);
        }

        baggage.setAllowances(allowances);
        return baggage;
    }
}
