package com.pnr.aggregator.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.List;

/**
 * -@Data: Lombok annotation for getter/setter generation.
 * --Auto-generates getters and setters for all fields
 * --Creates equals(), hashCode(), and toString() methods
 * --Simplifies DTO development
 * =========
 * -@JsonInclude(NON_NULL): Jackson annotation at class level
 * --Applies to ALL fields in this class
 * --Only includes non-null fields in JSON output
 * --Reduces JSON payload size by excluding null/empty values
 * --Example: ticketUrl only appears if passenger has a ticket
 * --WithoutIT: All fields would be serialized to JSON even when null;
 * ---increasing payload size and cluttering the response.
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PassengerDTO {
    private Integer passengerNumber;
    private String customerId;
    private String fullName;
    private String seat;
    private String ticketUrl;

    // Baggage allowance
    private String allowanceUnit;
    private Integer checkedAllowanceValue;
    private Integer carryOnAllowanceValue;

    /**
     * Fallback messages specific to this passenger
     * Contains errors/fallback reasons from:
     * - Baggage service (default allowance used)
     * - Ticket service (ticket unavailable)
     * Only included in JSON when fallback occurred ([@JsonInclude].NON_NULL at
     * class
     * level)
     */
    private List<String> passengerFallbackMsg;
}
