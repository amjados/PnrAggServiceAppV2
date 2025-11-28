package com.pnr.aggregator.model.entity;

import lombok.Data;

import java.util.List;

/**
 * @Data: Lombok annotation for boilerplate reduction
 * - Generates getters/setters automatically
 * - Implements equals(), hashCode(), and toString()
 * - Keeps entity classes clean and maintainable
 */
@Data
public class Ticket {
    private String bookingReference;
    private Integer passengerNumber;
    private String ticketUrl;

    /**
     * Fallback messages for ticket data
     * Set when ticket service is unavailable (circuit breaker open)
     * Contains unavailability reason
     */
    private List<String> ticketFallbackMsg;
}
