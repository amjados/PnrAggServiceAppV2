# Fallback Messages Implementation

## Overview
This document describes the implementation of entity-specific fallback message arrays that replace the generic `warnings` array in the PNR Aggregator service.

## Implementation Summary

### 1. DTO Changes (Data Transfer Objects)

#### PassengerDTO.java
**Added Field:**
```java
/**
 * Fallback messages specific to this passenger
 * Contains errors/fallback reasons from:
 * - Baggage service (default allowance used)
 * - Ticket service (ticket unavailable)
 * Only included in JSON when fallback occurred (@JsonInclude.NON_NULL at class level)
 */
private List<String> passengerFallbackMsg;
```

**Purpose:** Aggregates baggage and ticket fallback messages per passenger

---

#### FlightDTO.java
**Added Field:**
```java
/**
 * Fallback messages specific to this flight
 * Contains warnings when flight data is from cache (MongoDB unavailable)
 * Only included in JSON when fallback occurred (@JsonInclude.NON_NULL at class level)
 */
private List<String> flightFallbackMsg;
```

**Purpose:** Shows when flight data is stale (from cache)

---

#### BookingResponse.java
**Changes:**
1. **Removed:** `private List<String> warnings;`
2. **Added:**
```java
/**
 * PNR-level fallback messages
 * Contains errors/fallback reasons for trip data (from cache)
 * Only included in JSON when fallback occurred
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
private List<String> pnrFallbackMsg;
```

**Purpose:** Shows PNR-level issues (Trip service fallback)

---

### 2. Entity Changes (Domain Models)

#### Trip.java
**Added Field:**
```java
/**
 * Fallback messages for PNR-level trip data
 * Set when trip data comes from cache (MongoDB unavailable)
 * Contains cache timestamp and unavailability reason
 */
private List<String> pnrFallbackMsg;
```

---

#### Baggage.java
**Added Field:**
```java
/**
 * Fallback messages for baggage data
 * Set when default baggage allowance is used (service unavailable)
 * Contains default allowance details
 */
private List<String> baggageFallbackMsg;
```

---

#### Ticket.java
**Added Field:**
```java
/**
 * Fallback messages for ticket data
 * Set when ticket service is unavailable (circuit breaker open)
 * Contains unavailability reason
 */
private List<String> ticketFallbackMsg;
```

---

### 3. Service Layer Changes

#### TripService.java - getTripFallback()
**Sets messages when returning cached trip data:**
```java
cachedTrip.setPnrFallbackMsg(List.of(
    "Trip data from cache - MongoDB unavailable",
    "Cache timestamp: " + cacheTime.toString()
));
```

**When:** Circuit breaker is OPEN or MongoDB connection fails

---

#### BaggageService.java - getBaggageFallback()
**Sets messages when returning default allowance:**
```java
defaultBaggage.setBaggageFallbackMsg(List.of(
    "Using default baggage allowance - service unavailable",
    "Default: 25kg checked, 7kg carry-on"
));
```

**When:** Circuit breaker is OPEN or baggage data not found

---

#### TicketService.java - getTicketFallback()
**Sets messages when ticket unavailable:**
```java
fallbackTicket.setTicketFallbackMsg(List.of(
    "Ticket service unavailable",
    "Ticket data cannot be retrieved at this time"
));
```

**When:** Circuit breaker is OPEN or MongoDB connection fails

---

### 4. Aggregator Changes

#### BookingAggregatorService.java - mergeData()

**Key Changes:**

1. **Removed generic warnings array logic**
2. **Added PNR-level fallback messages:**
```java
if (trip.getPnrFallbackMsg() != null && !trip.getPnrFallbackMsg().isEmpty()) {
    response.setPnrFallbackMsg(trip.getPnrFallbackMsg());
}
```

3. **Added per-passenger fallback message aggregation:**
```java
List<String> passengerMessages = new ArrayList<>();

// Add baggage fallback messages (applies to all passengers)
if (baggage.getBaggageFallbackMsg() != null && !baggage.getBaggageFallbackMsg().isEmpty()) {
    passengerMessages.addAll(baggage.getBaggageFallbackMsg());
}

// Add ticket-specific fallback messages
if (ticket != null && ticket.getTicketFallbackMsg() != null && !ticket.getTicketFallbackMsg().isEmpty()) {
    passengerMessages.addAll(ticket.getTicketFallbackMsg());
}

// Set only if messages exist
if (!passengerMessages.isEmpty()) {
    dto.setPassengerFallbackMsg(passengerMessages);
}
```

4. **Added flight-level fallback messages:**
```java
// If trip is from cache, add flight-specific fallback messages
if (trip.isFromCache()) {
    dto.setFlightFallbackMsg(List.of(
        "Flight " + f.getFlightNumber() + " data from cache",
        "Flight data may not be up-to-date"
    ));
}
```

---

## JSON Output Examples

### SUCCESS Response (MongoDB Available)
```json
{
  "pnr": "GHTW42",
  "cabinClass": "Economy",
  "status": "SUCCESS",
  "passengers": [
    {
      "passengerNumber": 1,
      "fullName": "James Morgan McGill",
      "seat": "12A",
      "allowanceUnit": "kg",
      "checkedAllowanceValue": 25,
      "carryOnAllowanceValue": 7,
      "ticketUrl": "https://tickets.example.com/GHTW42-1"
    }
  ],
  "flights": [
    {
      "flightNumber": "EK231",
      "departureAirport": "DXB",
      "arrivalAirport": "JFK"
    }
  ]
}
```

**Note:** No fallback message fields present (due to @JsonInclude.NON_NULL)

---

### DEGRADED Response (MongoDB Unavailable)
```json
{
  "pnr": "GHTW42",
  "cabinClass": "Economy",
  "status": "DEGRADED",
  "passengers": [
    {
      "passengerNumber": 1,
      "fullName": "James Morgan McGill",
      "seat": "12A",
      "allowanceUnit": "kg",
      "checkedAllowanceValue": 25,
      "carryOnAllowanceValue": 7,
      "passengerFallbackMsg": [
        "Using default baggage allowance - service unavailable",
        "Default: 25kg checked, 7kg carry-on",
        "Ticket service unavailable",
        "Ticket data cannot be retrieved at this time"
      ]
    },
    {
      "passengerNumber": 2,
      "fullName": "Charles McGill",
      "seat": "12B",
      "allowanceUnit": "kg",
      "checkedAllowanceValue": 25,
      "carryOnAllowanceValue": 7,
      "passengerFallbackMsg": [
        "Using default baggage allowance - service unavailable",
        "Default: 25kg checked, 7kg carry-on",
        "Ticket service unavailable",
        "Ticket data cannot be retrieved at this time"
      ]
    }
  ],
  "flights": [
    {
      "flightNumber": "EK231",
      "departureAirport": "DXB",
      "arrivalAirport": "JFK",
      "flightFallbackMsg": [
        "Flight EK231 data from cache",
        "Flight data may not be up-to-date"
      ]
    }
  ],
  "fromCache": true,
  "cacheTimestamp": "2025-11-27T07:47:05.028Z",
  "pnrFallbackMsg": [
    "Trip data from cache - MongoDB unavailable",
    "Cache timestamp: 2025-11-27T07:47:05.028Z"
  ]
}
```

---

## Benefits of This Approach

### 1. **Entity-Specific Context**
Each entity (passenger, flight, PNR) shows its own specific issues rather than generic warnings

### 2. **Better Debugging**
Developers can quickly identify which service failed and for which entity

### 3. **Client Flexibility**
Frontend can display messages per entity (e.g., show baggage warning next to baggage info)

### 4. **Clean JSON**
Fallback fields only appear when needed (@JsonInclude.NON_NULL)

### 5. **Scalability**
Easy to add new fallback scenarios without affecting other entities

---

## Message Flow

```
┌─────────────────┐
│  TripService    │ → Sets pnrFallbackMsg → Trip entity
└─────────────────┘

┌─────────────────┐
│ BaggageService  │ → Sets baggageFallbackMsg → Baggage entity
└─────────────────┘

┌─────────────────┐
│ TicketService   │ → Sets ticketFallbackMsg → Ticket entity
└─────────────────┘

                    ↓
            ┌──────────────────┐
            │ BookingAggregator│
            │   mergeData()    │
            └──────────────────┘
                    ↓
    ┌───────────────────────────────────┐
    │   Aggregates messages per entity:  │
    │                                    │
    │   • pnrFallbackMsg → Response      │
    │   • baggageFallbackMsg → Passenger │
    │   • ticketFallbackMsg → Passenger  │
    │   • Flight messages from Trip      │
    └───────────────────────────────────┘
                    ↓
            BookingResponse DTO
```

---

## Testing Recommendations

### 1. **Test MongoDB Down**
- Stop MongoDB container
- Verify all fallback messages appear correctly
- Check each entity has appropriate messages

### 2. **Test Partial Failures**
- Trip available, Baggage unavailable
- Verify only passenger baggage messages appear

### 3. **Test Success Case**
- All services available
- Verify NO fallback message fields in JSON

### 4. **Test Individual Services**
- Use circuit breaker actuator endpoints
- Force individual circuit breakers to OPEN state
- Verify only affected entity messages appear

---

## Migration from Old System

### Before (Generic Warnings)
```json
"warnings": [
  "Trip data from cache - MongoDB unavailable",
  "Using default baggage allowance"
]
```

### After (Entity-Specific Messages)
```json
"pnrFallbackMsg": [
  "Trip data from cache - MongoDB unavailable",
  "Cache timestamp: 2025-11-27T07:47:05.028Z"
],
"passengers": [
  {
    "passengerFallbackMsg": [
      "Using default baggage allowance - service unavailable",
      "Default: 25kg checked, 7kg carry-on"
    ]
  }
],
"flights": [
  {
    "flightFallbackMsg": [
      "Flight EK231 data from cache",
      "Flight data may not be up-to-date"
    ]
  }
]
```

---

## Files Modified

### DTOs
1. `PassengerDTO.java` - Added `passengerFallbackMsg`
2. `FlightDTO.java` - Added `flightFallbackMsg`
3. `BookingResponse.java` - Added `pnrFallbackMsg`, removed `warnings`

### Entities
4. `Trip.java` - Added `pnrFallbackMsg`
5. `Baggage.java` - Added `baggageFallbackMsg`
6. `Ticket.java` - Added `ticketFallbackMsg`

### Services
7. `TripService.java` - Updated `getTripFallback()`
8. `BaggageService.java` - Updated `getBaggageFallback()`
9. `TicketService.java` - Updated `getTicketFallback()`
10. `BookingAggregatorService.java` - Updated `mergeData()`

---

## Conclusion

This implementation provides granular, entity-specific fallback messages that improve debugging, client-side display flexibility, and overall system observability. The use of `@JsonInclude(JsonInclude.Include.NON_NULL)` ensures clean JSON responses when no fallbacks occur.
