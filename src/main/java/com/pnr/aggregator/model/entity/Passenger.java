package com.pnr.aggregator.model.entity;

import lombok.Data;

/**
 * -@Data: Lombok annotation for boilerplate code generation.
 * --Auto-generates getters: getFirstName(), getMiddleName(), getLastName(),
 * etc.
 * --Auto-generates setters: setFirstName(), setMiddleName(), setLastName(),
 * etc.
 * --Generates equals(), hashCode(), and toString() methods
 * --Simplifies POJO (Plain Old Java Object) development
 */
@Data
public class Passenger {
    private String firstName;
    private String middleName;
    private String lastName;
    private Integer passengerNumber;
    private String customerId;
    private String seat;
}
