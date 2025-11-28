package com.pnr.aggregator.exception;

/**
 * Custom exception for service unavailability scenarios
 *
 * EXTENDS RuntimeException:
 * - Unchecked exception that doesn't require explicit handling
 * - Automatically propagates through call stack
 * - Spring converts to appropriate HTTP error response
 *
 * WHEN THROWN:
 * - Circuit breaker is OPEN and no cached data available
 * - MongoDB is down and fallback mechanisms exhausted
 * - Trip service cannot serve request even with cache
 *
 * EXCEPTION HANDLING:
 * - Caught in BookingController.getBooking() method
 * - Converted to HTTP 503 (Service Unavailable) response
 * - Indicates temporary service degradation
 *
 * CIRCUIT BREAKER INTEGRATION:
 * - Thrown by fallback methods when cache is empty
 * - Signals complete service failure (both primary and fallback)
 * - Client should retry after circuit breaker closes
 *
 * USAGE:
 * throw new ServiceUnavailableException("Trip service temporarily unavailable");
 */
public class ServiceUnavailableException extends RuntimeException {
    public ServiceUnavailableException(String message) {
        super(message);
    }
}
