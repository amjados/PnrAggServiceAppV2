package com.pnr.aggregator.model.entity;

import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.Data;

/**
 * -@Data: Lombok annotation for automatic code generation.
 * --Generates getters and setters for all fields
 * --Creates equals(), hashCode(), and toString() methods
 * --Reduces boilerplate code for entity/model classes
 */
@Data
public class Flight {
    private String flightNumber;
    private String departureAirport;
    private String departureTimeStamp;
    private String arrivalAirport;
    private String arrivalTimeStamp;

    /**
     * Parsed departure date-time for internal processing and filtering
     * -@JsonIgnore: Excluded from JSON serialization (API response uses departureTimeStamp string)
     * --WHY: Used for comparing flight times, filtering upcoming flights, sorting by departure
     * --Populated asynchronously from departureTimeStamp by DataTypeConverter in TripService
     */
    @JsonIgnore
    private LocalDateTime departureDateTime;
    
    /**
     * Parsed arrival date-time for internal processing
     * -@JsonIgnore: Excluded from JSON serialization (API response uses arrivalTimeStamp string)
     * --WHY: Used for flight duration calculations and time-based logic
     * --Populated asynchronously from arrivalTimeStamp by DataTypeConverter in TripService
     */
    @JsonIgnore
    private LocalDateTime arrivalDateTime;

}
