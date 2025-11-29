package com.pnr.aggregator.service;

import com.pnr.aggregator.exception.PNRNotFoundException;
import com.pnr.aggregator.exception.ServiceUnavailableException;
import com.pnr.aggregator.model.entity.Flight;
import com.pnr.aggregator.model.entity.Passenger;
import com.pnr.aggregator.model.entity.Trip;
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

    private CircuitBreaker circuitBreaker;

    /**
     * -@PostConstruct: Lifecycle callback executed after dependency injection.
     * --Called automatically after all [@Autowired] dependencies are injected
     * --Runs once during bean initialization, before the bean is put into service
     * --Ideal for initialization logic that requires injected dependencies
     * --Here: Initializes the circuit breaker instance from the registry
     * --WithoutIT: init() won't be called automatically;
     * ---circuit breaker would remain null.
     */
    @jakarta.annotation.PostConstruct
    public void init() {
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker("tripServiceCB");
        log.info("TripService Circuit Breaker initialized: {}", circuitBreaker.getName());
    }

    /**
     * Get trip information with circuit breaker protection
     * 
     * Circuit Breaker Config:
     * - Name: tripServiceCB
     * - Fallback: getTripFallback (returns cached data)
     * - Opens after: 10% failure rate over 10 calls
     * - Waits: 10 seconds before testing recovery
     * 
     * Note: Caching handled manually in fallback to avoid serializing Future
     * objects
     */
    public Future<Trip> getTripInfo(String pnr) {
        log.info("[CB-BEFORE] TripService call for PNR: {} | State: {}", pnr, circuitBreaker.getState());

        // Check if circuit is open
        if (!circuitBreaker.tryAcquirePermission()) {
            log.warn("[CB-REJECTED] Circuit is OPEN - calling fallback for PNR: {}", pnr);
            // Don't call onError() here - rejection is already tracked by CB
            return getTripFallback(pnr, new Exception("Circuit breaker is OPEN"));
        }

        long start = System.nanoTime();
        Promise<Trip> promise = Promise.promise();

        // NoSQL Injection Prevention: Use parameterized query
        // JsonObject automatically escapes special characters
        // PNR validated by controller ([@Pattern]) - only [A-Z0-9]{6} allowed
        JsonObject query = new JsonObject().put("bookingReference", pnr);

        mongoClient.findOne("trips", query, null, ar -> {
            long duration = System.nanoTime() - start;

            if (ar.succeeded()) {
                if (ar.result() == null) {
                    log.warn("Trip not found for PNR: {}", pnr);
                    // PNRNotFoundException is in ignoreExceptions - doesn't count as failure
                    circuitBreaker.onSuccess(duration, java.util.concurrent.TimeUnit.NANOSECONDS);
                    promise.fail(new PNRNotFoundException("PNR not found: " + pnr));
                } else {
                    Trip trip = mapToTrip(ar.result());
                    trip.setFromCache(false);

                    // Manually cache for fallback
                    Cache cache = cacheManager.getCache("trips");
                    if (cache != null) {
                        cache.put(pnr, trip);
                        log.debug("Cached trip data for PNR: {}", pnr);
                    }

                    circuitBreaker.onSuccess(duration, java.util.concurrent.TimeUnit.NANOSECONDS);
                    promise.complete(trip);
                    log.info("Trip fetched successfully for PNR: {}", pnr);
                }
            } else {
                log.error("MongoDB error fetching trip for PNR: {}", pnr, ar.cause());
                circuitBreaker.onError(duration, java.util.concurrent.TimeUnit.NANOSECONDS, ar.cause());

                // Use fallback
                getTripFallback(pnr, new Exception(ar.cause())).onComplete(fallbackResult -> {
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
