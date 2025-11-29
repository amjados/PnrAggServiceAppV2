package com.pnr.aggregator.model.entity;

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
}
