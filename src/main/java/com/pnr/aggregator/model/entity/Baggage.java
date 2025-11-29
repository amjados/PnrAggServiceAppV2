package com.pnr.aggregator.model.entity;

import lombok.Data;

import java.util.List;

/**
 * -@Data: Lombok annotation for POJO boilerplate code
 * --Auto-generates getters/setters for all fields
 * --Creates equals(), hashCode(), toString() implementations
 * --Enables clean, concise entity definitions
 */
@Data
public class Baggage {
    private String bookingReference;
    private List<BaggageAllowance> allowances;

    // Circuit breaker metadata
    private boolean fromCache;
    private boolean fromDefault;

    /**
     * Fallback messages for baggage data
     * Set when default baggage allowance is used (service unavailable)
     * Contains default allowance details
     */
    private List<String> baggageFallbackMsg;
}
