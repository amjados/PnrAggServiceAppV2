package com.pnr.aggregator.exception;

/**
 * Custom exception for PNR (Passenger Name Record) not found scenarios
 *
 * EXTENDS RuntimeException:
 * - RuntimeException is an unchecked exception (doesn't require try-catch or
 * throws declaration)
 * - Propagates up the call stack automatically
 * - Spring handles it gracefully in [@RestController] methods
 *
 * WHEN THROWN:
 * - TripService throws this when PNR doesn't exist in MongoDB
 * - Indicates a valid request for non-existent data (HTTP 404)
 *
 * EXCEPTION HANDLING:
 * - Caught in BookingController.getBooking() method
 * - Converted to HTTP 404 (Not Found) response
 * - Not counted as circuit breaker failure (in ignoreExceptions list)
 *
 * USAGE:
 * throw new PNRNotFoundException("PNR not found: ABC123");
 */
public class PNRNotFoundException extends RuntimeException {
    public PNRNotFoundException(String message) {
        super(message);
    }
}
