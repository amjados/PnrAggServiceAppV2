package com.pnr.aggregator.model.entity;

import lombok.Data;

import java.time.Instant;
import java.util.List;

/**
 * -@Data: Lombok annotation for automatic boilerplate code generation.
 * --Generates getters for all fields (getBookingReference(), getCabinClass(),
 * etc.)
 * --Generates setters for all fields (setBookingReference(), setCabinClass(),
 * etc.)
 * --Creates equals() and hashCode() methods based on all fields
 * --Creates toString() method with field values
 * --Eliminates ~50+ lines of boilerplate code for this class
 */
@Data
public class Trip {
    private String bookingReference;
    private String cabinClass;
    private List<Passenger> passengers;
    private List<Flight> flights;

    // Circuit breaker and cache metadata
    private boolean fromCache;
    private Instant cacheTimestamp;

    /**
     * Fallback messages for PNR-level trip data
     * Set when trip data comes from cache (MongoDB unavailable)
     * Contains cache timestamp and unavailability reason
     */
    private List<String> pnrFallbackMsg;
}
