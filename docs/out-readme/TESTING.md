# Testing Guide - Circuit Breaker Scenarios

This document outlines comprehensive testing scenarios for the PNR Aggregator service with Circuit Breaker pattern.

## Quick Start

### Automated Testing (Recommended)

```powershell
# Run complete automated test
.\test-files\circuit-breaker-test.ps1
```

**Benefits:**
- Complete circuit breaker lifecycle testing
- Automated MongoDB control
- Detailed metrics and summaries
- Reproducible results

## Prerequisites

- Docker installed and running
- Maven installed
- JDK 21 or higher

## Test Environment Setup

```bash
# Start MongoDB and Redis
docker-compose up -d

# Build application
mvn clean install

# Run application
mvn spring-boot:run
```

Wait for application to start (check logs for "Started Application").

---

## Test Scenario 1: Normal Operation (Circuit CLOSED)

**Purpose**: Verify normal operation with MongoDB available

### Steps:

```bash
# Test endpoint with sample PNR
curl http://localhost:8080/booking/GHTW42 | jq
```

### Expected Result:

- HTTP Status: 200 OK
- Response status: "SUCCESS"
- All passenger and flight data present
- Passenger 2 has ticketUrl, Passenger 1 does not (expected)

### Expected Response:

```json
{
  "pnr": "GHTW42",
  "cabinClass": "ECONOMY",
  "status": "SUCCESS",
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
  "flights": [...]
}
```

### Verification:

```bash
# Check circuit breaker health - should be UP
curl http://localhost:8080/actuator/health/circuitbreakers | jq
```

Expected: All circuit breakers in CLOSED state

---

## Test Scenario 2: Trigger Circuit Breaker (MongoDB Failure)

**Purpose**: Simulate MongoDB failure and verify circuit breaker opens

**Automated:** Use `.\test-files\circuit-breaker-test.ps1` for complete automated testing.

### Manual Steps:

```bash
# 1. Stop MongoDB to simulate failure
docker-compose stop mongodb

# 2. Make multiple requests to trigger circuit breaker
# Need >10 calls with >50% failure rate
for i in {1..15}; do
  echo "Request $i:"
  curl -s http://localhost:8080/booking/GHTW42 | jq -r '.status // .error'
  sleep 1
done
```

### Expected Behavior:

- **First 5-10 requests**: Slow responses (~5s MongoDB timeout)
- **After threshold**: Fast fail (<0.05s) with DEGRADED or ERROR
- **Circuit state**: CLOSED → OPEN (after 50% failure rate)

### Verification:

```bash
# Check circuit breaker state
curl http://localhost:8080/actuator/health/circuitbreakers | jq
```

Expected: `tripServiceCB` state is OPEN, failure rate >50%

### Check Application Logs:

```
WARN c.p.a.service.TripService - Circuit OPEN for TripService - using fallback for PNR: GHTW42
```

---

## Test Scenario 3: Fallback Response (Circuit OPEN)

**Purpose**: Verify fallback mechanisms when circuit is OPEN

### Prerequisites:

- MongoDB is stopped (from Scenario 2)
- Circuit breaker is OPEN (verified in Scenario 2)

### Steps:

```bash
# Make request with circuit OPEN
curl http://localhost:8080/booking/GHTW42 | jq
```

### Expected Result - Option A (Cached Data Available):

- HTTP Status: 200 OK
- Response status: "DEGRADED"
- fromCache: true
- warnings array contains: "Trip data from cache - MongoDB unavailable"

```json
{
  "pnr": "GHTW42",
  "status": "DEGRADED",
  "fromCache": true,
  "cacheTimestamp": "2025-11-25T10:30:00Z",
  "warnings": [
    "Trip data from cache - MongoDB unavailable",
    "Using default baggage allowance"
  ],
  "passengers": [...],
  "flights": [...]
}
```

### Expected Result - Option B (No Cache Available):

- HTTP Status: 503 Service Unavailable
- Clear error message indicating service is temporarily unavailable

```json
{
  "error": "ServiceUnavailable",
  "message": "Booking service temporarily unavailable. Please try again later.",
  "timestamp": "2025-11-25T10:30:00Z",
  "circuitBreakerState": "OPEN"
}
```

### Verification:

- Response is returned immediately (no 5-second MongoDB timeout)
- Warnings accurately reflect data sources

---

## Test Scenario 4: Circuit Breaker Metrics

**Purpose**: Verify metrics collection and visibility

### Steps:

```bash
# 1. Check general health
curl http://localhost:8080/actuator/health | jq

# 2. Check specific circuit breaker health
curl http://localhost:8080/actuator/health/circuitbreakers | jq

# 3. Check circuit breaker events
curl http://localhost:8080/actuator/circuitbreakerevents | jq

# 4. Check metrics for specific circuit breaker
curl http://localhost:8080/actuator/metrics/resilience4j.circuitbreaker.calls | jq

# 5. Get detailed metrics for tripServiceCB
curl "http://localhost:8080/actuator/metrics/resilience4j.circuitbreaker.calls?tag=name:tripServiceCB&tag=kind:successful" | jq
```

### Expected Metrics:

- Total calls made
- Successful vs failed calls
- Circuit breaker state transitions
- Failure rates

### Sample Event Response:

```json
{
  "circuitBreakerEvents": [
    {
      "circuitBreakerName": "tripServiceCB",
      "type": "STATE_TRANSITION",
      "creationTime": "2025-11-25T10:30:00.123Z",
      "stateTransition": "CLOSED_TO_OPEN",
      "errorMessage": "Failure rate threshold exceeded"
    }
  ]
}
```

---

## Test Scenario 5: Auto-Recovery (Circuit HALF_OPEN → CLOSED)

**Purpose**: Verify automatic recovery when MongoDB becomes available

**Automated:** Use `.\test-files\circuit-breaker-test.ps1` which handles timing and verification automatically.

### Manual Steps:

```bash
# 1. Verify circuit is OPEN
curl http://localhost:8080/actuator/health/circuitbreakers | jq '.components.circuitBreakers.details.tripServiceCB.state'

# 2. Restart MongoDB
docker-compose start mongodb
sleep 10  # Wait for MongoDB to be ready

# 3. Wait for circuit transition (60 seconds configured)
echo "Waiting 60 seconds for HALF_OPEN transition..."
sleep 60

# 4. Make test request (HALF_OPEN allows 3 test calls)
curl http://localhost:8080/booking/GHTW42 | jq

# 5. Check circuit state
curl http://localhost:8080/actuator/health/circuitbreakers | jq
```

### Expected Behavior:

1. Circuit starts OPEN (blocking all requests)
2. After 60s → transitions to HALF_OPEN
3. 3 test calls execute (MongoDB is healthy)
4. Success rate >50% → circuit transitions to CLOSED
5. Normal operation resumes

### Verification:

Circuit state progression: OPEN → HALF_OPEN → CLOSED

---

## Test Scenario 6: Partial Service Failure

**Purpose**: Verify independent circuit breakers per service

### Steps:

```bash
# 1. With MongoDB running, manually cause a specific service to fail
# (This requires code modification or specific test setup)

# 2. Alternative: Test with invalid PNR (tests error handling)
curl http://localhost:8080/booking/INVALID_PNR | jq
```

### Expected Result for Invalid PNR:

- HTTP Status: 404 Not Found
- Error: "Not Found"
- Circuit breaker does NOT open (business exception, not infrastructure failure)

```json
{
  "error": "NotFound",
  "message": "PNR not found: INVALID_PNR",
  "timestamp": "2025-11-25T10:30:00Z"
}
```

### Verification:

```bash
# Circuit breaker should still be CLOSED
curl http://localhost:8080/actuator/health/circuitbreakers | jq
```

Expected: `ignoreExceptions` configuration prevents business exceptions from triggering circuit breaker.

---

## Test Scenario 7: Cache Population and Retrieval

**Purpose**: Verify Redis caching mechanism for fallback data

### Steps:

```bash
# 1. With MongoDB running, make initial request to populate cache
curl http://localhost:8080/booking/GHTW42 | jq

# 2. Stop MongoDB
docker-compose stop mongodb

# 3. Trigger circuit breaker (multiple failed requests)
for i in {1..15}; do
  curl -s http://localhost:8080/booking/GHTW42 > /dev/null
  sleep 1
done

# 4. Request same PNR - should return cached data
curl http://localhost:8080/booking/GHTW42 | jq
```

### Expected Result:

- HTTP Status: 200 OK
- status: "DEGRADED"
- fromCache: true
- Data matches original request (before MongoDB was stopped)

### Verification:

- Cached data is returned even though MongoDB is unavailable
- Response includes warnings about cache usage
- cacheTimestamp is populated

---

## Test Scenario 8: Stress Test

**Purpose**: Verify circuit breaker behavior under load

### Steps:

```bash
# 1. MongoDB running - test normal load
for i in {1..100}; do
  curl -s http://localhost:8080/booking/GHTW42 > /dev/null &
done
wait

# 2. Stop MongoDB and test failure load
docker-compose stop mongodb
for i in {1..100}; do
  curl -s http://localhost:8080/booking/GHTW42 > /dev/null &
done
wait

# 3. Check metrics
curl http://localhost:8080/actuator/metrics/resilience4j.circuitbreaker.calls | jq
```

### Expected Behavior:

- Circuit opens after failure rate threshold
- No cascading failures
- Application remains responsive
- Metrics accurately reflect load

---

## Test Scenario 9: Multiple PNRs

**Purpose**: Verify circuit breaker applies consistently across different PNRs

### Steps:

```bash
# Test with different PNRs
curl http://localhost:8080/booking/GHTW42 | jq
curl http://localhost:8080/booking/ABC123 | jq
```

### Expected Result:

Both requests succeed with different data, circuit breaker state is shared across all requests.

---

## Cleanup

```bash
# Stop application (Ctrl+C in terminal where mvn spring-boot:run is running)

# Stop and remove containers
docker-compose down

# Optional: Remove MongoDB data volume
docker-compose down -v
```

---

## Common Issues and Troubleshooting

### Issue: Circuit Breaker Not Opening

**Symptoms**: MongoDB stopped but circuit stays CLOSED

**Solutions**:
1. Ensure minimum 10 calls made (`minimumNumberOfCalls: 10`)
2. Verify failure rate exceeds 50%
3. Check logs for actual errors
4. Verify exceptions are not in `ignoreExceptions` list

### Issue: Fallback Not Returning Data

**Symptoms**: 503 error even with cache populated

**Solutions**:
1. Verify cache was populated (make successful request first)
2. Check cache TTL hasn't expired (10 minutes)
3. Ensure `@Cacheable` annotation present on service methods
4. Check logs for cache lookup attempts

### Issue: Circuit Not Recovering

**Symptoms**: Circuit stays OPEN even after MongoDB restart

**Solutions**:
1. Wait full 60 seconds for transition to HALF_OPEN
2. Ensure MongoDB is fully started and accepting connections
3. Check MongoDB connection settings in `application.yml`
4. Verify test calls in HALF_OPEN state are succeeding

---

## Success Criteria

All tests pass when:

- Normal operation returns SUCCESS status
- Circuit opens after 50% failure rate
- Fallback data returned when circuit OPEN
- Metrics accurately reflect circuit state
- Auto-recovery works after 60 seconds
- Business exceptions don't trigger circuit breaker
- Application remains responsive under failure
- Redis cache provides fallback data
- Warnings indicate degraded state
- Multiple PNRs handled correctly
