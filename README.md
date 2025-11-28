# PNR Aggregator Service with Circuit Breaker

Spring Boot + Vert.x PNR (Passenger Name Record) aggregator service with Resilience4j Circuit Breaker pattern for handling MongoDB failures gracefully.

## Architecture

Service-only architecture with Circuit Breaker pattern protecting MongoDB operations:

```
Client → BookingController → BookingAggregatorService
                                      ↓
            ┌─────────────────────────┼─────────────────────────┐
            ↓                         ↓                         ↓
       TripService              BaggageService           TicketService
       [@CircuitBreaker]        [@CircuitBreaker]        [@CircuitBreaker]
            ↓                         ↓                         ↓
       [CB: CLOSED]             [CB: CLOSED]             [CB: CLOSED]
            ↓                         ↓                         ↓
       MongoDB                   MongoDB                   MongoDB
```
## Features

### Circuit Breaker Protection
- Independent circuit breakers for Trip, Baggage, and Ticket services
- Automatic failure detection (50% failure rate threshold)
- Auto-recovery after 60 seconds
- Graceful fallback mechanisms

### Fallback Strategies
- **Trip Service**: Returns Redis cached data when available
- **Baggage Service**: Returns default economy allowance (25kg checked, 7kg carry-on)
- **Ticket Service**: Returns null (same as missing ticket)

### Response States
- **SUCCESS**: All data fetched from MongoDB
- **DEGRADED**: Using Redis cached or default data (MongoDB unavailable)
- **ERROR 503**: No fallback data available

## Setup

### 1. Start Infrastructure
```bash
# Start MongoDB and Redis
docker-compose up -d
```

### 2. Build and Run
```bash
mvn clean install
mvn spring-boot:run
```

### 3. Test Normal Operation
```bash
curl http://localhost:8080/booking/GHTW42
```

**Expected Response (HTTP 200) - SUCCESS Status:**
```json
{
  "pnr": "GHTW42",
  "cabinClass": "ECONOMY",
  "status": "SUCCESS",
  "fromCache": null,
  "cacheTimestamp": null,
  "warnings": null,
  "passengers": [
    {
      "passengerNumber": 1,
      "fullName": "James Morgan McGill",
      "seat": "32D"
    },
    {
      "passengerNumber": 2,
      "customerId": "1216",
      "fullName": "Charles McGill",
      "seat": "31D",
      "ticketUrl": "emirates.com?ticket=someTicketRef"
    }
  ],
  "flights": [
    {
      "flightNumber": "EK231",
      "departureAirport": "DXB",
      "departureTimeStamp": "2025-11-11T02:25:00+00:00",
      "arrivalAirport": "IAD",
      "arrivalTimeStamp": "2025-11-11T08:10:00+00:00"
    }
  ],
  "baggage": {
    "checkedAllowance": 25,
    "carryOnAllowance": 7,
    "unit": "kg"
  }
}
```

**Field Descriptions:**
- `status`: "SUCCESS" - All data fetched from MongoDB
- `fromCache`: null - Data not from cache (direct from MongoDB)
- `cacheTimestamp`: null - No cache timestamp (fresh data)
- `warnings`: null - No warnings (normal operation)
- `passengers`: Array of passenger details with ticket URLs (if available)
- `flights`: Array of flight information
- `baggage`: Baggage allowance per passenger

**Why These Fields Are Null:**
- **Normal Operation**: When MongoDB is available, data comes directly from database
- **fromCache = null**: Indicates fresh data, not cached fallback
- **cacheTimestamp = null**: No timestamp needed for direct database queries
- **warnings = null**: No degraded service warnings

---

**Alternative Response (HTTP 200) - DEGRADED Status:**
```json
{
  "pnr": "GHTW42",
  "status": "DEGRADED",
  "fromCache": true,
  "cacheTimestamp": "2025-11-26T10:30:00Z",
  "warnings": [
    "Trip data from cache - MongoDB unavailable",
    "Using default baggage allowance"
  ],
  "passengers": [...],
  "flights": [...],
  "baggage": {
    "checkedAllowance": 25,
    "carryOnAllowance": 7,
    "unit": "kg",
    "isDefault": true
  }
}
```

**Field Descriptions (DEGRADED mode):**
- `status`: "DEGRADED" - Fallback data in use
- `fromCache`: true - Data retrieved from Redis cache
- `cacheTimestamp`: ISO timestamp when data was cached
- `warnings`: Array of messages explaining degraded state
- `baggage.isDefault`: true - Using default baggage values (not from MongoDB)

---

**Error Response (HTTP 503) - Service Unavailable:**
```json
{
  "error": "ServiceUnavailable",
  "message": "Booking service temporarily unavailable. Please try again later.",
  "timestamp": "2025-11-26T10:30:00Z",
  "circuitBreakerState": "OPEN"
}
```

**When This Occurs:**
- MongoDB is down AND no cached data available
- Circuit breaker is OPEN and blocking requests
- No fallback mechanisms can provide data
```

## Circuit Breaker Testing

### Automated Testing (Recommended)

**Run the automated test script:**
```powershell
.\test-files\circuit-breaker-test.ps1
```

The script automatically:
- Tests normal operation (MongoDB running)
- Simulates MongoDB failure and verifies circuit opens
- Tests fallback mechanisms (cached and default data)
- Verifies circuit recovery when MongoDB restarts
- Provides detailed metrics and summary

**What It Tests:**
- Phase 1: SUCCESS responses with MongoDB running
- Phase 2: Circuit opens after failure threshold
- Phase 3: Recovery with DEGRADED → SUCCESS transition
- Circuit states: CLOSED → OPEN → HALF_OPEN → CLOSED
- Response times: ~5s timeout → <0.05s fast fail (100x improvement)

### Manual Testing

```bash
# 1. Stop MongoDB
docker-compose stop mongodb

# 2. Make requests to trigger circuit breaker
for i in {1..15}; do
  curl http://localhost:8080/booking/GHTW42
  sleep 1
done

# 3. Check circuit state
curl http://localhost:8080/actuator/health/circuitbreakers

# 4. Restore MongoDB
docker-compose start mongodb
```

**Expected Behavior:**
- First 5-10 requests: Wait for MongoDB timeout (~5s)
- Circuit opens: CLOSED → OPEN (after 50% failure rate)
- Subsequent requests: Fast fail with DEGRADED or ERROR (<0.05s)
- After MongoDB restart: Circuit recovers (OPEN → HALF_OPEN → CLOSED)

### Test Scenario 2: Check Circuit State
```bash
# Check health endpoint
curl http://localhost:8080/actuator/health | jq

# Check circuit breaker metrics
curl http://localhost:8080/actuator/metrics/resilience4j.circuitbreaker.calls | jq

# Check circuit breaker events
curl http://localhost:8080/actuator/circuitbreakerevents | jq
```

**Expected Output 1: Health Endpoint**
```json
{
  "status": "UP",
  "components": {
    "circuitBreakers": {
      "status": "UP",
      "details": {
        "tripServiceCB": {
          "status": "UP",
          "details": {
            "failureRate": "-1.0%",
            "state": "CLOSED",
            "bufferedCalls": 0,
            "failedCalls": 0
          }
        }
      }
    },
    "redis": {
      "status": "UP",
      "details": { "version": "7.4.7" }
    }
  }
}
```
Shows application health, circuit breaker states (CLOSED/OPEN/HALF_OPEN), failure rates, and Redis connectivity.

**Expected Output 2: Metrics Endpoint**
```json
{
  "name": "resilience4j.circuitbreaker.calls",
  "measurements": [
    { "statistic": "COUNT", "value": 0.0 }
  ],
  "availableTags": [
    {
      "tag": "kind",
      "values": ["ignored", "failed", "successful"]
    },
    {
      "tag": "name",
      "values": ["ticketServiceCB", "baggageServiceCB", "tripServiceCB"]
    }
  ]
}
```
Provides call statistics. Use `?tag=name:tripServiceCB&tag=kind:failed` to filter specific circuit breaker metrics.

**Expected Output 3: Events Endpoint**
```json
{
  "circuitBreakerEvents": [
    {
      "circuitBreakerName": "tripServiceCB",
      "type": "STATE_TRANSITION",
      "creationTime": "2025-11-26T10:30:00.123Z",
      "stateTransition": "CLOSED_TO_OPEN"
    }
  ]
}
```
Lists recent state transitions and events (CLOSED↔OPEN↔HALF_OPEN).

### Test Scenario 3: Auto-Recovery
```bash
# Restart MongoDB
docker-compose start mongodb

# Wait for circuit to transition to HALF_OPEN (60 seconds)
sleep 60

# Next request will test recovery
curl http://localhost:8080/booking/GHTW42
```

Circuit closes on successful test calls, returning to normal operation.

## Circuit Breaker Configuration

```yaml
resilience4j:
  circuitbreaker:
    configs:
      default:
        slidingWindowSize: 100          # Sample size for failure rate
        minimumNumberOfCalls: 10        # Minimum calls before evaluation
        failureRateThreshold: 50        # Open at 50% failure rate
        waitDurationInOpenState: 60s    # Wait before testing recovery
        permittedNumberOfCallsInHalfOpenState: 3  # Test calls in HALF_OPEN
```

## Monitoring Endpoints

- **Health**: `http://localhost:8080/actuator/health`
- **Circuit Breakers**: `http://localhost:8080/actuator/circuitbreakers`
- **Circuit Events**: `http://localhost:8080/actuator/circuitbreakerevents`
- **Metrics**: `http://localhost:8080/actuator/metrics/resilience4j.circuitbreaker.calls`

## Project Structure

```
src/main/java/com/pnr/aggregator/
├── Application.java                      # Spring Boot entry point
├── controller/
│   └── BookingController.java            # REST endpoint
├── service/
│   ├── BookingAggregatorService.java     # Orchestrates parallel calls
│   ├── TripService.java                  # Trip operations + Circuit Breaker
│   ├── BaggageService.java               # Baggage operations + Circuit Breaker
│   └── TicketService.java                # Ticket operations + Circuit Breaker
├── config/
│   ├── VertxConfig.java                  # Vert.x + MongoDB client setup
│   ├── MongoDbProperties.java            # MongoDB configuration properties
│   ├── CacheConfig.java                  # Redis cache configuration
│   ├── CircuitBreakerLogger.java         # CB event logging
│   ├── WebConfig.java                    # CORS configuration
│   └── WebSocketConfig.java              # WebSocket configuration
├── exception/
│   ├── PNRNotFoundException.java
│   └── ServiceUnavailableException.java
└── model/
    ├── dto/
    │   ├── BookingResponse.java
    │   ├── PassengerDTO.java
    │   └── FlightDTO.java
    └── entity/
        ├── Trip.java
        ├── Baggage.java
        └── Ticket.java
```

## Data Model

### Collections

1. **trips** - Booking information with passengers and flights
2. **baggage** - Baggage allowances per passenger
3. **tickets** - Ticket URLs (not all passengers have tickets)

### Sample PNRs

- `GHTW42` - Economy booking with 2 passengers (only passenger 2 has ticket)
- `ABC123` - Business booking with 1 passenger

## Key Implementation Details

### Circuit Breaker Annotations
```java
@CircuitBreaker(name = "tripServiceCB", fallbackMethod = "getTripFallback")
@Cacheable(value = "trips", key = "#pnr")
public Future<Trip> getTripInfo(String pnr) { ... }
```

### Fallback Methods
```java
private Future<Trip> getTripFallback(String pnr, Exception ex) {
    // Try Redis cache first
    Trip cachedTrip = redisCache.get(pnr, Trip.class);
    if (cachedTrip != null) {
        cachedTrip.setFromCache(true);
        return Future.succeededFuture(cachedTrip);
    }
    // No cache - fail gracefully
    return Future.failedFuture(new ServiceUnavailableException(...));
}
```

### Parallel Operations
```java
// Fetch trip, baggage, and all tickets in parallel
return tripService.getTripInfo(pnr)
    .compose(trip -> {
        Future<Baggage> baggageFuture = baggageService.getBaggage(pnr);
        List<Future<Ticket>> ticketFutures = trip.getPassengers().stream()
            .map(p -> ticketService.getTicket(pnr, p.getPassengerNumber())
                .recover(err -> Future.succeededFuture(null)))
            .collect(Collectors.toList());
        
        return Future.all(baggageFuture, Future.all(ticketFutures))
            .map(cf -> mergeData(trip, baggageFuture.result(), ticketFutures));
    });
```

## Resilience Features

- Circuit breakers prevent cascading failures
- Fallback mechanisms ensure partial functionality
- Auto-recovery without manual intervention
- Health monitoring for operational visibility
- Metrics tracking

### Error Handling
- Business exceptions (PNR not found) don't trigger circuit breaker
- Infrastructure failures (MongoDB down) do trigger circuit breaker
- Missing tickets handled gracefully (not all passengers have tickets)
- Clear error messages for clients

### Features
- Parallel data fetching (Trip + Baggage + Tickets)
- Redis caching for frequently accessed data
- Reactive programming with Vert.x Futures

## Troubleshooting

### Circuit Won't Open
- Check minimum calls threshold (10 calls minimum)
- Verify failure rate exceeds 50%
- Ensure exceptions are not in `ignoreExceptions` list

### Fallback Not Working
- Verify fallback method signature matches (requires Exception parameter)
- Check Redis cache configuration
- Ensure fallback method doesn't throw exceptions
- Verify Redis is accessible: `docker-compose logs redis`

### Cache Not Populating
- Verify `@Cacheable` annotation on service methods
- Check Redis configuration in `application.yml`
- Ensure successful responses are cached
- Verify Redis container is running: `docker ps | grep redis`

## Technology Stack

- **Java**: JDK 21
- **Framework**: Spring Boot 3.2.0
- **Reactive**: Vert.x 4.5.0
- **Circuit Breaker**: Resilience4j 2.1.0
- **Database**: MongoDB 7.0
- **Cache**: Redis 7
- **Build**: Maven 3.9
- **Containerization**: Docker & Docker Compose
