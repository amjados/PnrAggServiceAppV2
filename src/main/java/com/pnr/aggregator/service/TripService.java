package com.pnr.aggregator.service;

import com.pnr.aggregator.exception.PNRNotFoundException;
import com.pnr.aggregator.exception.ServiceUnavailableException;
import com.pnr.aggregator.model.entity.Flight;
import com.pnr.aggregator.model.entity.Passenger;
import com.pnr.aggregator.model.entity.Trip;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Promise;
//import io.vertx.core.eventbus.EventBus;
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
import java.util.stream.Collectors;

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
     * 
     * WHY MANUAL CIRCUIT BREAKER (not @CircuitBreaker annotation):
     * --Problem with @CircuitBreaker(name="tripServiceCB",
     * fallbackMethod="getTripFallback"):
     * ---Spring AOP (Aspect-Oriented Programming) proxy intercepts method but can't
     * handle Vert.x async callbacks
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
    }

    /**
     * Handle MongoDB query result for trip retrieval
     */
    private Promise<Trip> onTripResult(AsyncResult<JsonObject> ar, String pnr, long start, Promise<Trip> promise) {
        {
            long duration = System.nanoTime() - start;

            if (ar.succeeded()) {
                if (ar.result() == null) {
                    log.warn("Trip not found for PNR: {}", pnr);
                    circuitBreaker.onSuccess(duration, java.util.concurrent.TimeUnit.NANOSECONDS);
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
                    promise.complete(trip);
                    log.info("Trip fetched successfully for PNR: {}", pnr);
                }
            } else {
                log.error("MongoDB error fetching trip for PNR: {}", pnr, ar.cause());
                circuitBreaker.onError(duration, java.util.concurrent.TimeUnit.NANOSECONDS, ar.cause());

                getTripFallback(pnr, new Exception(ar.cause())).onComplete(fallbackResult -> {
                    if (fallbackResult.succeeded()) {
                        promise.complete(fallbackResult.result());
                    } else {
                        promise.fail(fallbackResult.cause());
                    }
                });
            }
        }
        return promise;
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
            return getTripFallback(pnr, new Exception("Circuit breaker is OPEN"));
        }

        long start = System.nanoTime();
        Promise<Trip> promise = Promise.promise();

        JsonObject query = new JsonObject().put("bookingReference", pnr);

        mongoClient.findOne("trips", query, null, ar -> onTripResult(ar, pnr, start, promise));

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

    /**
     * Get all trips for a specific customer ID with circuit breaker protection
     * 
     * Searches MongoDB for trips where any passenger has the given customerId
     * Uses MongoDB query: { "passengers.customerId": customerId }
     * 
     * Circuit Breaker Config:
     * - Name: tripServiceCB
     * - Fallback: getTripsByCustomerIdFallback (returns cached data)
     * - Opens after: 10% failure rate over 10 calls
     * - Waits: 10 seconds before testing recovery
     * 
     * NoSQL Injection Prevention:
     * - Uses parameterized query with validated input
     * - customerId validated at controller level
     * 
     * -@param customerId The customer identifier to search for
     * -@return Future with list of trips matching the customer ID
     */
    public Future<List<Trip>> getTripsByCustomerId(String customerId) {
        log.info("[CB-BEFORE] TripService search for Customer ID: {} | State: {}", customerId,
                circuitBreaker.getState());

        // Check if circuit is open
        if (!circuitBreaker.tryAcquirePermission()) {
            log.warn("[CB-REJECTED] Circuit is OPEN - calling fallback for Customer ID: {}", customerId);
            return getTripsByCustomerIdFallback(customerId, new Exception("Circuit breaker is OPEN"));
        }

        long start = System.nanoTime();
        Promise<List<Trip>> promise = Promise.promise();

        // Query for trips where any passenger has this customerId
        JsonObject query = new JsonObject().put("passengers.customerId", customerId);

        mongoClient.find("trips", query, ar -> {
            long duration = System.nanoTime() - start;

            if (ar.succeeded()) {
                List<JsonObject> results = ar.result();

                if (results.isEmpty()) {
                    log.info("No trips found for Customer ID: {}", customerId);
                    circuitBreaker.onSuccess(duration, java.util.concurrent.TimeUnit.NANOSECONDS);
                    promise.complete(List.of());
                } else {
                    List<Trip> trips = results.stream()
                            .map(this::mapToTrip)
                            .peek(trip -> trip.setFromCache(false))
                            .collect(Collectors.toList());

                    // Cache the trips list for this customer ID
                    Cache cache = cacheManager.getCache("tripsByCustomer");
                    if (cache != null) {
                        cache.put(customerId, trips);
                        log.debug("Cached {} trip(s) for Customer ID: {}", trips.size(), customerId);
                    }

                    circuitBreaker.onSuccess(duration, java.util.concurrent.TimeUnit.NANOSECONDS);
                    log.info("Found {} trip(s) for Customer ID: {}", trips.size(), customerId);
                    promise.complete(trips);
                }
            } else {
                log.error("MongoDB error searching trips for Customer ID: {}", customerId, ar.cause());
                circuitBreaker.onError(duration, java.util.concurrent.TimeUnit.NANOSECONDS, ar.cause());

                getTripsByCustomerIdFallback(customerId, new Exception(ar.cause())).onComplete(fallbackResult -> {
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
     * Fallback method for getTripsByCustomerId when circuit is OPEN
     * 
     * Returns cached trips list if available, otherwise fails gracefully
     * This prevents cascading failures when MongoDB is down
     * Sets fallback messages on each trip to indicate cache usage
     * 
     * -@param customerId The customer identifier to search for
     * -@param ex The exception that triggered the fallback
     * -@return Future with cached trips or error
     */
    private Future<List<Trip>> getTripsByCustomerIdFallback(String customerId, Exception ex) {
        log.warn("Circuit OPEN for TripService - using fallback for Customer ID: {}. Reason: {}",
                customerId, ex.getMessage());

        // Try to get from cache
        Cache cache = cacheManager.getCache("tripsByCustomer");
        if (cache != null) {
            @SuppressWarnings("unchecked")
            List<Trip> cachedTrips = cache.get(customerId, List.class);

            if (cachedTrips != null && !cachedTrips.isEmpty()) {
                log.info("Returning {} cached trip(s) for Customer ID: {}", cachedTrips.size(), customerId);
                Instant cacheTime = Instant.now();

                // Mark all trips as from cache and add fallback messages
                cachedTrips.forEach(trip -> {
                    trip.setFromCache(true);
                    trip.setCacheTimestamp(cacheTime);
                    trip.setPnrFallbackMsg(List.of(
                            "Trip data from cache - MongoDB unavailable",
                            "Cache timestamp: " + cacheTime.toString()));
                });

                return Future.succeededFuture(cachedTrips);
            }
        }

        // No cache available - fail gracefully
        log.error("No cached data available for Customer ID: {}", customerId);
        return Future.failedFuture(
                new ServiceUnavailableException("Trip service temporarily unavailable for customer " + customerId));
    }

    
}
