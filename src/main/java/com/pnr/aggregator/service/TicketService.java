package com.pnr.aggregator.service;

import com.pnr.aggregator.model.entity.Ticket;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * -@Service: Registers this class as a Spring service bean.
 * --Enables component scanning and dependency injection
 * --Contains business logic for ticket retrieval
 * --WithoutIT: Service won't be discovered;
 * ---ticket retrieval functionality would be unavailable.
 * =========
 * -@Slf4j: Lombok annotation for automatic SLF4J logger
 * --Generates logger field without boilerplate code
 * --WithoutIT: No logger available;
 * ---compilation errors on log statements.
 */
@Service
@Slf4j
public class TicketService {

    /**
     * -@Autowired: Dependency injection for MongoClient.
     * --Injects Vert.x MongoClient for non-blocking MongoDB queries
     * --WithoutIT: mongoClient would be null;
     * ---all database queries would fail.
     */
    @Autowired
    private MongoClient mongoClient;

    /**
     * -@Autowired: Dependency injection for CircuitBreakerRegistry.
     * --Provides access to circuit breaker configurations
     * --WithoutIT: circuitBreakerRegistry would be null;
     * ---circuit breaker initialization would fail.
     */
    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    private CircuitBreaker circuitBreaker;

    /**
     * -@PostConstruct: Bean initialization callback.
     * --Runs after dependency injection completes
     * --Retrieves circuit breaker instance for this service
     * --WithoutIT: init() won't be called automatically;
     * ---circuit breaker would remain null.
     * 
     * WHY MANUAL CIRCUIT BREAKER (not @CircuitBreaker annotation):
     * --@CircuitBreaker AOP (Aspect-Oriented Programming) proxy can't handle Vert.x
     * Future<T> async callbacks
     * properly
     * --Manual pattern allows fallback to return null for missing tickets (valid
     * scenario)
     * --See TripService.init() for detailed explanation of manual vs annotation
     * approach
     * -- See if (!circuitBreaker.tryAcquirePermission()) { ... } in getTicket()
     * method
     */
    @jakarta.annotation.PostConstruct
    public void init() {
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker("ticketServiceCB");
        log.info("TicketService Circuit Breaker initialized: {}", circuitBreaker.getName());
    }

    /**
     * Fetch ticket info from MongoDB by PNR and passenger number
     * 
     * Circuit Breaker protects against MongoDB failures
     * Fallback returns ticket with null URL and fallback message
     * 
     * NoSQL Injection Prevention:
     * - Uses parameterized query with type-safe values
     * - PNR validated at controller level, passengerNumber is primitive int
     * (type-safe)
     */
    public Future<Ticket> getTicket(String pnr, int passengerNumber) {
        log.info("[CB-BEFORE] TicketService call for PNR: {}, Passenger: {} | State: {}", pnr, passengerNumber,
                circuitBreaker.getState());

        // Check if circuit is open
        if (!circuitBreaker.tryAcquirePermission()) {
            log.warn("[CB-REJECTED] Circuit is OPEN - calling fallback for PNR: {}, Passenger: {}", pnr,
                    passengerNumber);
            // Don't call onError() here - rejection is already tracked by CB
            return getTicketFallback(pnr, passengerNumber, new Exception("Circuit breaker is OPEN"));
        }

        long start = System.nanoTime();
        Promise<Ticket> promise = Promise.promise();

        // NoSQL Injection Prevention: Parameterized query with type-safe values
        // PNR validated at controller level, passengerNumber is primitive int
        // (type-safe)
        JsonObject query = new JsonObject()
                .put("bookingReference", pnr)
                .put("passengerNumber", passengerNumber);

        mongoClient.findOne("tickets", query, null, ar -> {
            long duration = System.nanoTime() - start;

            if (ar.succeeded()) {
                if (ar.result() == null) {
                    // IMPORTANT: Missing ticket is NOT a circuit breaker failure
                    // Some passengers legitimately don't have tickets
                    log.debug("No ticket found for PNR: {}, Passenger: {}", pnr, passengerNumber);
                    circuitBreaker.onSuccess(duration, java.util.concurrent.TimeUnit.NANOSECONDS);
                    promise.fail(new RuntimeException("Ticket not found"));
                } else {
                    Ticket ticket = mapToTicket(ar.result());
                    circuitBreaker.onSuccess(duration, java.util.concurrent.TimeUnit.NANOSECONDS);
                    promise.complete(ticket);
                    log.info("Ticket fetched successfully for PNR: {}, Passenger: {}", pnr, passengerNumber);
                }
            } else {
                log.error("MongoDB error fetching ticket for PNR: {}, Passenger: {}", pnr, passengerNumber, ar.cause());
                circuitBreaker.onError(duration, java.util.concurrent.TimeUnit.NANOSECONDS, ar.cause());

                // Use fallback
                getTicketFallback(pnr, passengerNumber, new Exception(ar.cause())).onComplete(fallbackResult -> {
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
     * Fallback: Return ticket with null URL and fallback message
     * 
     * This is graceful - not all passengers have tickets
     * Circuit breaker protects against MongoDB failures,
     * but missing tickets are handled by .recover() in aggregator
     * Sets ticketFallbackMsg to indicate service unavailability
     */
    private Future<Ticket> getTicketFallback(String pnr, int passengerNumber, Exception ex) {
        log.warn("Circuit OPEN for TicketService - PNR: {}, Passenger: {}", pnr, passengerNumber);

        // Return ticket with null URL and fallback reason
        Ticket fallbackTicket = new Ticket();
        fallbackTicket.setBookingReference(pnr);
        fallbackTicket.setPassengerNumber(passengerNumber);
        fallbackTicket.setTicketUrl(null);

        // Set fallback messages for ticket data
        fallbackTicket.setTicketFallbackMsg(List.of(
                "Ticket service unavailable",
                "Ticket data cannot be retrieved at this time"));

        return Future.succeededFuture(fallbackTicket);
    }

    private Ticket mapToTicket(JsonObject doc) {
        Ticket ticket = new Ticket();
        ticket.setBookingReference(doc.getString("bookingReference"));
        ticket.setPassengerNumber(doc.getInteger("passengerNumber"));
        ticket.setTicketUrl(doc.getString("ticketUrl"));
        return ticket;
    }
}
