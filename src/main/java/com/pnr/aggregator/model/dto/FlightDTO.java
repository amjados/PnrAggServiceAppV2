package com.pnr.aggregator.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.List;

/**
 * -@Data: Lombok annotation for boilerplate reduction
 * --Generates getters/setters automatically
 * --Provides equals(), hashCode(), and toString()
 * --Clean DTO class definition
 * =========
 * -@JsonInclude(NON_NULL): Jackson serialization control
 * --Only serializes non-null fields to JSON
 * --Omits null fields from API response
 * --Cleaner JSON output for clients
 * --WithoutIT: Null fields would be included in JSON response;
 * increasing response size and reducing readability.
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FlightDTO {
    private String flightNumber;
    private String departureAirport;
    private String departureTimeStamp;
    private String arrivalAirport;
    private String arrivalTimeStamp;

    /**
     * Fallback messages specific to this flight
     * Contains warnings when flight data is from cache (MongoDB unavailable)
     * Only included in JSON when fallback occurred ([@JsonInclude].NON_NULL at
     * class
     * level)
     */
    private List<String> flightFallbackMsg;
}
