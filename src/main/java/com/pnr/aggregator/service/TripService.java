package com.pnr.aggregator.service;

import com.pnr.aggregator.exception.PNRNotFoundException;
import com.pnr.aggregator.exception.ServiceUnavailableException;
import com.pnr.aggregator.model.entity.Flight;
import com.pnr.aggregator.model.entity.Passenger;
import com.pnr.aggregator.model.entity.Trip;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * -@Service: Marks this class as a Spring service component.
 * --Registers this class as a Spring bean for dependency injection
 * --Contains business logic for trip data retrieval
 * --WithoutIT: Service won't be discovered;
 * ---trip data retrieval would be unavailable.
 * =========
 * -@Slf4j: Lombok annotation for automatic logger creation
 * --Generates: private static final Logger log =
 * LoggerFactory.getLogger(TripService.class)
 * --WithoutIT: No logger available;
 * ---compilation errors on log statements.
 */
@Service
@Slf4j
public class TripService {

    /**
     * -@Autowired: Dependency injection for Vert.x MongoClient
     * --Injects MongoClient bean configured in VertxConfig
     * --Used for non-blocking MongoDB operations
     * --WithoutIT: mongoClient would be null;
     * ---all trip data queries would fail.
     */
    @Autowired
    private MongoClient mongoClient;

    /**
     * -@Autowired: Dependency injection for Spring CacheManager.
     * --Injects CacheManager bean configured in CacheConfig (Redis)
     * --Used for caching trip data as fallback when MongoDB is unavailable
     * --WithoutIT: cacheManager would be null;
     * ---fallback caching wouldn't work.
     */
    @Autowired
    private CacheManager cacheManager;

    /**
     * -@Autowired: Dependency injection for Resilience4j CircuitBreakerRegistry.
     * --Injects CircuitBreakerRegistry to create circuit breaker instances
     * --Registry manages all circuit breakers in the application
     * --WithoutIT: circuitBreakerRegistry would be null;
     * ---circuit breaker initialization would fail.
     */
    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    /**
     * -@Autowired: Dependency injection for Resilience4j RetryRegistry.
     * --Injects RetryRegistry to create retry instances
     * --Registry manages all retry configurations in the application
     * --WithoutIT: retryRegistry would be null;
     * ---retry initialization would fail.
     */
    @Autowired
    private RetryRegistry retryRegistry;

    /**
     * -@Autowired: Dependency injection for Vert.x EventBus
     * --Injects EventBus configured in VertxConfig
     * --Enables publishing retry events to WebSocket clients
     * --WithoutIT: eventBus would be null;
     * ---retry events won't be broadcast to WebSocket clients.
     */
    @Autowired
    private EventBus eventBus;

    private CircuitBreaker circuitBreaker;
    private Retry retry;
    private RetryConfig retryConfig;

    /**
     * -@PostConstruct: Lifecycle callback executed after dependency injection.
     * --Called automatically after all [@Autowired] dependencies are injected
     * --Runs once during bean initialization, before the bean is put into service
     * --Ideal for initialization logic that requires injected dependencies
     * --Here: Initializes the circuit breaker instance from the registry
     * --WithoutIT: init() won't be called automatically;
     * ---circuit breaker would remain null.
     * 
     * WHY MANUAL CIRCUIT BREAKER (not @CircuitBreaker annotation):
     * --Problem with @CircuitBreaker(name="tripServiceCB",
     * fallbackMethod="getTripFallback"):
     * ---Spring AOP proxy intercepts method but can't handle Vert.x async callbacks
     * properly
     * ---Fallback method can't access mongoClient callback context to cache data
     * manually
     * ---With Future<T> return type, fallback can't execute
     * cacheManager.getCache().put() inside callback
     * ---Promise completion pattern breaks with AOP proxy interception
     * --Solution: Manual pattern with tryAcquirePermission() +
     * onSuccess()/onError()
     * ---Full control over caching inside Vert.x async callback (required for
     * fallback strategy)
     * ---Direct access to Promise context for proper async completion
     * ---Explicit fallback invocation with complete control over timing and data
     * -- See if (!circuitBreaker.tryAcquirePermission()) { ... } in getTripInfo()
     * method
     */
    @jakarta.annotation.PostConstruct
    public void init() {
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker("tripServiceCB");
        log.info("TripService Circuit Breaker initialized: {}", circuitBreaker.getName());

        this.retry = retryRegistry.retry("tripServiceRetry");
        this.retryConfig = retry.getRetryConfig();
        long waitDurationMs = 1000L;
        try {
            if (retryConfig != null && retryConfig.getIntervalFunction() != null) {
                Long v = retryConfig.getIntervalFunction().apply(1);
                if (v != null)
                    waitDurationMs = v;
            }
        } catch (Exception e) {
            log.debug("Unable to read retry interval function, using default: {}ms", waitDurationMs, e);
        }

        log.info("TripService Retry initialized: {} (maxAttempts={}, waitDuration={}ms)",
                retry.getName(),
                (retryConfig != null ? retryConfig.getMaxAttempts() : "n/a"),
                waitDurationMs);
    }

    /**
     * Get trip information with circuit breaker protection and manual retry
     * 
     * Circuit Breaker Config:
     * - Name: tripServiceCB
     * - Fallback: getTripFallback (returns cached data)
     * - Opens after: 10% failure rate over 10 calls
     * - Waits: 10 seconds before testing recovery
     * 
     * Retry Config:
     * - Name: tripServiceRetry
     * - Max Attempts: 3
     * - Wait Duration: 1000ms (with exponential backoff: 1s, 2s, 4s)
     * - Execution Order: Manual retry wraps Circuit Breaker
     * 
     * WHY MANUAL RETRY (not @Retry annotation):
     * --Problem with @Retry annotation:
     * ---Spring AOP proxy can't handle Vert.x async callbacks properly
     * ---Retry needs to wrap the entire Future chain, not just method entry
     * ---Can't access Promise context inside MongoDB callback for proper retry
     * ---Blocked by manual tryAcquirePermission() check (returns Future, not
     * throws)
     * --Solution: Manual retry pattern with executeWithRetry() helper
     * ---Full control over Vert.x async context and Promise completion
     * ---Integrates seamlessly with manual circuit breaker pattern
     * ---Proper event publishing to EventBus for WebSocket notifications
     * ---Consistent with project's manual pattern architecture
     * 
     * Note: Caching handled manually in fallback to avoid serializing Future
     * objects
     */
    public Future<Trip> getTripInfo(String pnr) {
        log.debug("[RETRY-DEBUG] TripService.getTripInfo() ENTRY - PNR: {} | Thread: {}", pnr,
                Thread.currentThread().getName());

        // Manual Retry Pattern: Wrap the entire operation
        return executeWithRetry(pnr, 1);
    }

    /**
     * Execute MongoDB trip query with manual retry logic
     * Integrates with manual circuit breaker pattern
     * 
     * @param pnr            The booking reference
     * @param currentAttempt Current retry attempt (1-based)
     * @return Future with Trip data
     */
    private Future<Trip> executeWithRetry(String pnr, int currentAttempt) {
        log.info("[CB-BEFORE] TripService call for PNR: {} | State: {} | Attempt: {}/{}",
                pnr, circuitBreaker.getState(), currentAttempt, retryConfig.getMaxAttempts());

        // Circuit OPEN -> immediate fallback (do not throw)
        if (!circuitBreaker.tryAcquirePermission()) {
            log.warn("[CB-REJECTED] Circuit is OPEN - calling fallback for PNR: {} | Attempt: {}/{}",
                    pnr, currentAttempt, retryConfig.getMaxAttempts());

            if (currentAttempt > 1) {
                publishRetryEvent("tripServiceRetry", "RETRY_EXHAUSTED_CB_OPEN",
                        String.format("Circuit breaker OPEN after %d attempts for PNR: %s",
                                currentAttempt - 1, pnr));
            }

            return getTripFallback(pnr, new Exception("Circuit breaker is OPEN"));
        }

        long start = System.nanoTime();
        Promise<Trip> promise = Promise.promise();

        JsonObject query = new JsonObject().put("bookingReference", pnr);

        mongoClient.findOne("trips", query, null, ar -> {
            long duration = System.nanoTime() - start;

            if (ar.succeeded()) {
                if (ar.result() == null) {
                    log.warn("Trip not found for PNR: {} | Attempt: {}", pnr, currentAttempt);
                    circuitBreaker.onSuccess(duration, java.util.concurrent.TimeUnit.NANOSECONDS);

                    // If this succeeded in the sense of no DB error but not found, don't retry
                    promise.fail(new PNRNotFoundException("PNR not found: " + pnr));
                } else {
                    Trip trip = mapToTrip(ar.result());
                    trip.setFromCache(false);

                    Cache cache = cacheManager.getCache("trips");
                    if (cache != null) {
                        cache.put(pnr, trip);
                        log.debug("Cached trip data for PNR: {}", pnr);
                    }

                    circuitBreaker.onSuccess(duration, java.util.concurrent.TimeUnit.NANOSECONDS);

                    if (currentAttempt > 1) {
                        publishRetryEvent("tripServiceRetry", "RETRY_SUCCESS",
                                String.format("Request succeeded after %d attempts for PNR: %s",
                                        currentAttempt - 1, pnr));
                    }

                    promise.complete(trip);
                    log.info("Trip fetched successfully for PNR: {} | Attempt: {}", pnr, currentAttempt);
                }
            } else {
                log.error("MongoDB error fetching trip for PNR: {} | Attempt: {}/{} | Cause: {}",
                        pnr, currentAttempt, retryConfig.getMaxAttempts(), ar.cause());
                circuitBreaker.onError(duration, java.util.concurrent.TimeUnit.NANOSECONDS, ar.cause());

                // Decide whether to retry
                if (shouldRetry(ar.cause(), currentAttempt)) {
                    long waitDuration = calculateWaitDuration(currentAttempt);
                    log.info("[RETRY-ATTEMPT] Will retry in {}ms for PNR: {} | Attempt: {}/{}",
                            waitDuration, pnr, currentAttempt, retryConfig.getMaxAttempts());

                    publishRetryEvent("tripServiceRetry", "RETRY",
                            String.format("Retrying after %dms (attempt %d/%d) for PNR: %s",
                                    waitDuration, currentAttempt + 1, retryConfig.getMaxAttempts(), pnr));

                    try {
                        // Use Vert.x timer to schedule the retry on the same Vert.x instance
                        io.vertx.core.Vertx vertx = io.vertx.core.Vertx.currentContext() != null
                                ? io.vertx.core.Vertx.currentContext().owner()
                                : io.vertx.core.Vertx.vertx();

                        vertx.setTimer(waitDuration, id -> {
                            executeWithRetry(pnr, currentAttempt + 1).onComplete(res -> {
                                if (res.succeeded()) {
                                    promise.complete(res.result());
                                } else {
                                    promise.fail(res.cause());
                                }
                            });
                        });
                    } catch (Exception e) {
                        log.warn("Failed to schedule retry timer, falling back: {}", e.getMessage());
                        getTripFallback(pnr, new Exception(ar.cause())).onComplete(fb -> {
                            if (fb.succeeded())
                                promise.complete(fb.result());
                            else
                                promise.fail(fb.cause());
                        });
                    }
                } else {
                    // Exhausted or not retryable -> fallback
                    log.error("[RETRY-EXHAUSTED] No more retries for PNR: {} | Attempts: {}",
                            pnr, currentAttempt);

                    publishRetryEvent("tripServiceRetry", "RETRY_EXHAUSTED",
                            String.format("All %d retry attempts failed for PNR: %s",
                                    currentAttempt, pnr));

                    getTripFallback(pnr, new Exception(ar.cause())).onComplete(fallbackResult -> {
                        if (fallbackResult.succeeded()) {
                            promise.complete(fallbackResult.result());
                        } else {
                            promise.fail(fallbackResult.cause());
                        }
                    });
                }
            }
        });

        return promise.future();
    }

    /**
     * Determine if the operation should be retried based on exception type and
     * attempt count
     */
    private boolean shouldRetry(Throwable throwable, int currentAttempt) {
        if (currentAttempt >= retryConfig.getMaxAttempts())
            return false;

        if (throwable == null)
            return false;

        // Don't retry on not found
        if (throwable instanceof PNRNotFoundException)
            return false;

        String name = throwable.getClass().getName();
        boolean isRetryable = name.contains("MongoException") || name.contains("IOException")
                || name.contains("TimeoutException") || name.contains("VertxException");

        log.debug("[RETRY-CHECK] Exception {} is retryable: {}", throwable.getClass().getSimpleName(), isRetryable);
        return isRetryable;
    }

    /**
     * Calculate wait duration (ms) for given attempt. Uses retryConfig interval
     * function.
     */
    private long calculateWaitDuration(int currentAttempt) {
        try {
            // RetryConfig's interval function accepts attempt number (1-based)
            return retryConfig.getIntervalFunction().apply(currentAttempt);
        } catch (Exception e) {
            // Fallback to 1000ms
            return 1000L;
        }
    }

    /**
     * Publish retry event to Vert.x EventBus
     */
    private void publishRetryEvent(String retryName, String eventType, String message) {
        try {
            JsonObject event = new JsonObject()
                    .put("type", "RETRY_EVENT")
                    .put("retryName", retryName)
                    .put("eventType", eventType)
                    .put("message", message)
                    .put("timestamp", Instant.now().toString());

            if (eventBus != null)
                eventBus.publish("system.events", event);
            log.debug("[RETRY-EVENT] Published {} event: {}", eventType, message);
        } catch (Exception e) {
            log.warn("Failed to publish retry event to EventBus: {}", e.getMessage());
        }
    }

    /**
     * Fallback method invoked when circuit is OPEN
     * 
     * Returns cached trip data if available, otherwise fails
     * This prevents cascading failures when MongoDB is down
     * Sets pnrFallbackMsg to indicate cache usage
     */
    private Future<Trip> getTripFallback(String pnr, Exception ex) {
        log.warn("Circuit OPEN for TripService - using fallback for PNR: {}. Reason: {}",
                pnr, ex.getMessage());

        // Try to get from cache
        Cache cache = cacheManager.getCache("trips");
        if (cache != null) {
            Trip cachedTrip = cache.get(pnr, Trip.class);

            if (cachedTrip != null) {
                log.info("Returning cached trip data for PNR: {}", pnr);
                cachedTrip.setFromCache(true);
                Instant cacheTime = Instant.now();
                cachedTrip.setCacheTimestamp(cacheTime);

                // Set fallback messages for PNR-level trip data
                cachedTrip.setPnrFallbackMsg(List.of(
                        "Trip data from cache - MongoDB unavailable",
                        "Cache timestamp: " + cacheTime.toString()));

                return Future.succeededFuture(cachedTrip);
            }
        }

        // No cache available - fail gracefully
        log.error("No cached data available for PNR: {}", pnr);
        return Future.failedFuture(
                new ServiceUnavailableException("Trip service temporarily unavailable"));
    }

    private Trip mapToTrip(JsonObject doc) {
        Trip trip = new Trip();
        trip.setBookingReference(doc.getString("bookingReference"));
        trip.setCabinClass(doc.getString("cabinClass"));

        // Map passengers
        JsonArray passengersArray = doc.getJsonArray("passengers");
        List<Passenger> passengers = new ArrayList<>();
        for (int i = 0; i < passengersArray.size(); i++) {
            JsonObject p = passengersArray.getJsonObject(i);
            Passenger passenger = new Passenger();
            passenger.setFirstName(p.getString("firstName"));
            passenger.setMiddleName(p.getString("middleName"));
            passenger.setLastName(p.getString("lastName"));
            passenger.setPassengerNumber(p.getInteger("passengerNumber"));
            passenger.setCustomerId(p.getString("customerId"));
            passenger.setSeat(p.getString("seat"));
            passengers.add(passenger);
        }
        trip.setPassengers(passengers);

        // Map flights
        JsonArray flightsArray = doc.getJsonArray("flights");
        List<Flight> flights = new ArrayList<>();
        for (int i = 0; i < flightsArray.size(); i++) {
            JsonObject f = flightsArray.getJsonObject(i);
            Flight flight = new Flight();
            flight.setFlightNumber(f.getString("flightNumber"));
            flight.setDepartureAirport(f.getString("departureAirport"));
            flight.setDepartureTimeStamp(f.getString("departureTimeStamp"));
            flight.setArrivalAirport(f.getString("arrivalAirport"));
            flight.setArrivalTimeStamp(f.getString("arrivalTimeStamp"));
            flights.add(flight);
        }
        trip.setFlights(flights);

        return trip;
    }
}
