# PNR Aggregator Service - Technical Guide

## Architecture Overview

This is a **Spring Boot 3.4 + Vert.x 4.4.9** microservice aggregating Passenger Name Record (PNR) data from MongoDB with Resilience4j Circuit Breaker protection. The hybrid architecture combines Spring's dependency injection with Vert.x's non-blocking I/O for parallel MongoDB queries.

**Key Components:**
- `BookingAggregatorService` - Orchestrates parallel calls to Trip/Baggage/Ticket services using `Future.all()`
- `TripService`, `BaggageService`, `TicketService` - Each protected by independent circuit breakers with fallback strategies
- `PNRWebSocketHandler` - Broadcasts PNR fetch events via Vert.x EventBus to connected WebSocket clients
- MongoDB (Vert.x reactive client) + Redis (Spring Cache) for fallback data

## Critical Version Compatibility

**MUST maintain these exact versions together:**
```xml
<vertx.version>4.4.9</vertx.version>
<mongodb-driver-reactivestreams>4.11.4</mongodb-driver-reactivestreams>
<mongodb-driver-core>4.11.4</mongodb-driver-core>
```

**Why:** Vert.x 4.4.9 requires MongoDB driver 4.x (needs `StreamFactoryFactory` class removed in 5.x). Spring Boot 3.4.0 parent tries to override to 5.x - explicit version override in `pom.xml` prevents this. Never upgrade MongoDB driver without verifying Vert.x compatibility.

## Development Workflow

### Local Development
```bash
# Start infrastructure (MongoDB on 27017, Redis on 6379)
docker-compose up -d mongodb redis

# Build and run application
mvn clean install
mvn spring-boot:run

# Test endpoint
curl http://localhost:8080/booking/GHTW42
```

### Full Docker Stack
```bash
docker-compose up -d  # Includes app + MongoDB + Redis + Swagger UI (port 8081)
docker-compose logs -f pnr-aggregator  # View application logs
```

**Pre-loaded test data:** PNRs `GHTW42`, `ABC123` automatically seeded via `mongo-init/init-mongo.js`

### Testing Circuit Breaker
```powershell
# Run automated circuit breaker test (2-3 minutes)
.\test-files\circuit-breaker-test.ps1

# Manual testing:
# 1. Normal call: curl http://localhost:8080/booking/GHTW42 → status "SUCCESS"
# 2. Stop MongoDB: docker-compose stop mongodb
# 3. Wait 10+ calls or check actuator: curl http://localhost:8080/actuator/circuitbreakers
# 4. Circuit opens → status "DEGRADED" with cached/fallback data
# 5. Restart: docker-compose start mongodb → circuit recovers to CLOSED after 10s
```

## Code Patterns & Conventions

### Circuit Breaker Implementation
**Pattern:** Manual circuit breaker with `tryAcquirePermission()` + `onSuccess()`/`onError()` (not `@CircuitBreaker` annotation).

```java
// In service methods (see TripService.getTripInfo)
if (!circuitBreaker.tryAcquirePermission()) {
    return getTripFallback(pnr, new Exception("Circuit breaker is OPEN"));
}

long start = System.nanoTime();
mongoClient.findOne("collection", query, null, ar -> {
    long duration = System.nanoTime() - start;
    if (ar.succeeded()) {
        circuitBreaker.onSuccess(duration, TimeUnit.NANOSECONDS);
        // Cache data for fallback, then complete promise
    } else {
        circuitBreaker.onError(duration, TimeUnit.NANOSECONDS, ar.cause());
        // Return fallback
    }
});
```

**Fallback Strategies (per service):**
- `TripService` → Return Redis cached data with `fromCache=true`, `cacheTimestamp` set
- `BaggageService` → Return default allowance (25kg checked, 7kg carry-on) with `isDefault=true`
- `TicketService` → Return null (missing tickets are valid - not all passengers have them)

### Parallel Operations with Vert.x Futures
**Always** use `Future.all()` for independent operations. See `BookingAggregatorService.aggregateBooking()`:

```java
Future<Baggage> baggageFuture = baggageService.getBaggageInfo(pnr);
List<Future<Ticket>> ticketFutures = passengers.stream()
    .map(p -> ticketService.getTicket(pnr, p.getPassengerNumber()))
    .collect(Collectors.toList());

return Future.all(baggageFuture, Future.all(ticketFutures))
    .map(cf -> mergeData(...));  // All operations complete before merge
```

### Response Status Field
`BookingResponse.status` indicates degradation state:
- `"SUCCESS"` - All data from MongoDB (normal operation)
- `"DEGRADED"` - Using cached/fallback data (MongoDB unavailable)
- HTTP 503 only when no fallback available (Trip service has no cache)

### WebSocket Event Broadcasting
Vert.x EventBus bridges service layer to WebSocket layer. After aggregation:

```java
// In BookingAggregatorService
vertx.eventBus().publish("pnr.fetched", new JsonObject()
    .put("pnr", pnr)
    .put("status", response.getStatus())
    .put("timestamp", Instant.now().toEpochMilli()));

// PNRWebSocketHandler subscribes on @PostConstruct, broadcasts to all sessions
```

## Configuration Management

### Environment Variables (Docker)
Override via `docker-compose.yml` or runtime `-e` flags:
```bash
MONGODB_HOST=pnr-mongodb  # Service name in docker-compose
MONGODB_PORT=27017
REDIS_HOST=pnr-redis
SERVER_PORT=8080
JAVA_OPTS="-Xmx512m -Xms256m -XX:+UseG1GC"  # Container-optimized JVM
```

### Circuit Breaker Tuning
Edit `application.yml` → `resilience4j.circuitbreaker.configs.default`:
- `slidingWindowSize: 100` - Track last N calls for failure rate
- `failureRateThreshold: 10` - Open circuit at 10% failure rate
- `waitDurationInOpenState: 10s` - Wait before testing recovery (OPEN → HALF_OPEN)
- `minimumNumberOfCalls: 10` - Minimum calls before calculating failure rate

**Instance names:** `tripServiceCB`, `baggageServiceCB`, `ticketServiceCB` (referenced in service `@PostConstruct` initialization)

## Key Files for Common Tasks

**Add new aggregated field:**
1. `model/dto/BookingResponse.java` - Add DTO field
2. `service/BookingAggregatorService.java` - Fetch data in `aggregateBooking()`, merge in `mergeData()`
3. Add new service with circuit breaker if fetching from MongoDB

**Modify circuit breaker behavior:**
1. `application.yml` - Adjust thresholds/timeouts
2. `service/TripService.java` (or other service) - Change fallback logic in `get*Fallback()` methods

**Add WebSocket event type:**
1. `service/BookingAggregatorService.java` - Publish to new EventBus topic
2. `websocket/PNRWebSocketHandler.java` - Subscribe to topic in `@PostConstruct init()`

**Database changes:**
1. Collections: `trips`, `baggage`, `tickets` in `pnr_db`
2. Seed data: `mongo-init/init-mongo.js` (auto-loaded on MongoDB container startup)

## Testing & Debugging

**Actuator endpoints:**
- `/actuator/health` - Overall health + circuit breaker states
- `/actuator/circuitbreakers` - Detailed CB metrics (calls, failure rates, states)
- `/actuator/metrics` - JVM, HTTP request metrics

**PowerShell test suite** in `test-files/`:
- `circuit-breaker-test.ps1` - Full CB lifecycle test (recommended first test)
- `quick-test.ps1` - 30s smoke test
- `load-test.ps1` - Performance testing with CSV output

**WebSocket testing:**
Open `test-files/websocket-test.html` in browser, connect to `ws://localhost:8080/pnr-updates`

## Common Pitfalls

1. **MongoDB driver version mismatch** - Always keep Vert.x and MongoDB driver versions aligned (see compatibility section)
2. **Forgetting manual caching** - Circuit breaker fallbacks won't work without manual `cacheManager.getCache().put()` in service methods
3. **Blocking operations in Vert.x callbacks** - Never call blocking Spring/JDBC code inside `mongoClient` callbacks (use `vertx.executeBlocking()` if needed)
4. **Circuit breaker not initialized** - Services must call `circuitBreakerRegistry.circuitBreaker(name)` in `@PostConstruct` before use
5. **PNRNotFoundException triggers circuit** - It's in `ignoreExceptions` list (missing PNR is valid, not a failure)
