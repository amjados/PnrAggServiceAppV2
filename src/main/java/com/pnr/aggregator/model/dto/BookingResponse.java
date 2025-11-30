package com.pnr.aggregator.model.dto;

import lombok.Data;

import java.time.Instant;
import java.util.List;

/**
 * -@Data: Lombok annotation for boilerplate code generation.
 * --Generates getters/setters for all fields
 * --Creates equals(), hashCode(), and toString() methods
 * --Essential for DTO (Data Transfer Object) classes
 * The naming follows a common convention:
 * 
 * Response suffix: Used for objects that are directly returned from REST
 * endpoints (like BookingController)
 * DTO suffix: Used for nested data transfer objects that are components of a
 * larger response
 */
@Data
public class BookingResponse {
    private String pnr;
    private String cabinClass;
    private String status;
    private List<PassengerDTO> passengers;
    private List<FlightDTO> flights;

    // Degraded mode metadata
    private Boolean fromCache;
    private Instant cacheTimestamp;

    /**
     * PNR-level fallback messages
     * Contains errors/fallback reasons for trip data (from cache)
     * Only included in JSON when fallback occurred
     * =========
     * -@JsonInclude(NON_NULL): Jackson annotation for conditional JSON
     * serialization
     * --Only includes this field in JSON output if value is not null
     * --Keeps JSON response clean by omitting null fields
     * --Example: pnrFallbackMsg only appears when fallback occurs
     * --WithoutIT: pnrFallbackMsg field would always be included in JSON even when
     * ---null;
     * ---cluttering the response with empty/null fields.
     */
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private List<String> pnrFallbackMsg;
}
