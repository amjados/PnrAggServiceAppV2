package com.pnr.aggregator.model.entity;

import lombok.Data;

/**
 * -@Data: Lombok annotation for getter/setter generation
 * --Automatically generates getters and setters
 * --Provides equals(), hashCode(), and toString() methods
 * --Simplifies model class development
 */
@Data
public class BaggageAllowance {
    private Integer passengerNumber;
    private String allowanceUnit;
    private Integer checkedAllowanceValue;
    private Integer carryOnAllowanceValue;
}
