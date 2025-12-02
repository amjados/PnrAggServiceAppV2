# PNR Aggregator Service

A resilient PNR (Passenger Name Record) aggregation service built with Spring Boot and Vert.x, featuring circuit breaker patterns for graceful failure handling.

## Architecture

```
Client → Controller → Aggregator Service
                           ↓
        ┌──────────────────┼──────────────────┐
        ↓                  ↓                  ↓
   TripService      BaggageService     TicketService
   [CircuitBreaker] [CircuitBreaker]   [CircuitBreaker]
        ↓                  ↓                  ↓
    MongoDB            MongoDB            MongoDB

```
## Aggregator Service
## Component Architecture - Spring Boot + Vert.x

```
┌────────────────────────────────────────────────────────────────────────────────────┐
│                          PRESENTATION LAYER (Controllers)                          │
└────────────────────────────────────────────────────────────────────────────────────┘
                                         │
                                         │ HTTP GET /booking/{pnr}
                                         ▼
                        ┌──────────────────────────────────┐
                        │   BookingController              │
                        │   - @RestController              │
                        │   - @Validated                   │
                        ├──────────────────────────────────┤
                        │ + getBooking(pnr): Future        │
                        │ + handleValidationException()    │
                        └──────────────────────────────────┘
                                         │
                                         │ Calls
                                         ▼
┌────────────────────────────────────────────────────────────────────────────────────┐
│                           BUSINESS LOGIC LAYER (Services)                          │
└────────────────────────────────────────────────────────────────────────────────────┘
                                         │
                        ┌────────────────┴──────────────┐
                        │                               │
                        ▼                               │
        ┌───────────────────────────────────┐           │
        │  BookingAggregatorService         │           │
        │  - @Service                       │           │
        ├───────────────────────────────────┤           │
        │ - tripService                     │───────────┘
        │ - baggageService                  │
        │ - ticketService                   │
        │ - vertx                           │
        ├───────────────────────────────────┤
        │ + aggregateBooking(pnr)           │
        │   → CompositeFuture.all()         │
        └───────────────────────────────────┘
                        │
        ┌───────────────┼───────────────┐
        │               │               │
        ▼               ▼               ▼
┌─────────────┐ ┌───────────────┐ ┌──────────────┐
│ TripService │ │BaggageService │ │TicketService │
│             │ │               │ │              │
│ - mongodb   │ │ - mongodb     │ │ - mongodb    │
│ - redis     │ │ - breaker     │ │ - breaker    │
│ - breaker   │ │               │ │              │
├─────────────┤ ├───────────────┤ ├──────────────┤
│ +getTripInfo│ │+getBaggageInfo│ │+getTicket()  │
│ -fallback() │ │-fallback()    │ │.recover()    │
└─────────────┘ └───────────────┘ └──────────────┘
        │               │               │
        └───────────────┼───────────────┘
                        │
                        │ Async Event Bus
                        ▼
        ┌───────────────────────────────┐
        │   PnrEventConsumer            │
        │   - @Component                │
        ├───────────────────────────────┤
        │ + consumePnrEvents()          │
        │   → WebSocket broadcast       │
        └───────────────────────────────┘

┌────────────────────────────────────────────────────────────────────────────────────┐
│                         CONFIGURATION LAYER (@Configuration)                       │
└────────────────────────────────────────────────────────────────────────────────────┘

┌──────────────┐ ┌──────────────┐ ┌───────────────┐ ┌──────────────┐
│ VertxConfig  │ │ CacheConfig  │ │WebSocketConfig│ │  WebConfig   │
│              │ │              │ │               │ │              │
│ @Bean Vertx  │ │@Bean Redis   │ │@Bean STOMP    │ │@Bean CORS    │
│ @Bean Mongo  │ │ CacheManager │ │ MessageBroker │ │ AsyncSupport │
│ @Bean CB's   │ │              │ │               │ │              │
└──────────────┘ └──────────────┘ └───────────────┘ └──────────────┘

┌──────────────────┐ ┌──────────────────────┐
│MongoDbProperties │ │CircuitBreakerLogger  │
│@ConfigProperties │ │ Monitors CB states   │
└──────────────────┘ └──────────────────────┘

┌────────────────────────────────────────────────────────────────────────────────────┐
│                            DATA LAYER (Models)                                     │
└────────────────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────┐     ┌────────────────────────────────┐
│   ENTITIES (MongoDB Collections)│     │       DTOs (API Response)      │
├─────────────────────────────────┤     ├────────────────────────────────┤
│ • trips (collection)            │     │ • BookingResponse              │
│   - passengers[] (embedded)     │───▶│ • PassengerDTO                 │
│   - flights[] (embedded)        │     │ • FlightDTO                    │
│ • baggage (collection)          │     │                                │
│   - baggageAllowances[] (embed) │     │ Separates internal/external    │
│ • tickets (collection)          │     │ data models                    │
└─────────────────────────────────┘     └────────────────────────────────┘

┌─────────────────────────────────┐
│       EXCEPTIONS                │
├─────────────────────────────────┤
│ • PNRNotFoundException (404)    │
│ • ServiceUnavailableException   │
│   (503 - Circuit Breaker Open)  │
└─────────────────────────────────┘

┌────────────────────────────────────────────────────────────────────────────────────┐
│                         INFRASTRUCTURE (External)                                  │
└────────────────────────────────────────────────────────────────────────────────────┘

┌──────────────┐  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐
│   MongoDB    │  │  Redis Cache │  │  Vert.x      │  │  WebSocket   │
│              │  │              │  │  Event Bus   │  │  Clients     │
│ Collections: │  │ TTL: 5min    │  │              │  │              │
│ - trips      │  │ Pattern:     │  │ Address:     │  │ /ws/pnr      │
│ - baggage    │  │   trip:{pnr} │  │   pnr.events │  │              │
│ - tickets    │  │              │  │              │  │              │
└──────────────┘  └──────────────┘  └──────────────┘  └──────────────┘

```
## Features

### Resilience
- Circuit breaker protection for database operations
- Automatic failure detection and recovery
- Redis cache fallback for trip data
- Default values for baggage service
- Graceful degradation

### Performance
- Parallel data fetching using Vert.x
- Redis caching for frequently accessed data
- Reactive programming patterns

## Quick Start

### Run the Application

1. **Start infrastructure**
   ```bash
   docker-compose up -d
   ```

2. **Build and run**
   ```bash
   mvn clean install
   or
   mvn clean package -DskipTests

   mvn spring-boot:run
   ```

3. **Test the endpoint**
   ```bash
   curl http://localhost:8080/booking/GHTW42
   or
   http://localhost:8080/booking/customer/1099

   ```

## API Response Examples

### Success Response (HTTP 200)
When all services are operational:

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
    }
  ],
  "flights": [
    {
      "flightNumber": "EK231",
      "departureAirport": "DXB",
      "arrivalAirport": "IAD"
    }
  ],
  "baggage": {
    "checkedAllowance": 25,
    "carryOnAllowance": 7,
    "unit": "kg"
  }
}
```

### Degraded Response (HTTP 200)
When using fallback data:

```json
{
  "pnr": "GHTW42",
  "status": "DEGRADED",
  "fromCache": true,
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

### Error Response (HTTP 503)
When no fallback data is available:

```json
{
  "error": "ServiceUnavailable",
  "message": "Booking service temporarily unavailable. Please try again later."
}
```

## Testing Circuit Breaker

### Automated Test Script (Recommended)

Run the PowerShell test script for comprehensive circuit breaker testing:

```powershell
.\test-files\circuit-breaker-test.ps1
```

**What it tests:**
- Normal operation with MongoDB running
- Circuit breaker opens after failure threshold
- Fallback mechanisms (cached and default data)
- Circuit recovery after MongoDB restart
- Response time improvements

**Expected output:**
- Phase 1: SUCCESS responses (MongoDB up)
- Phase 2: Circuit opens, fast-fail with fallback data
- Phase 3: Circuit recovers (OPEN → HALF_OPEN → CLOSED)

## Monitoring

### Health Endpoints
- **Application Health**: `http://localhost:8080/actuator/health`
- **Circuit Breakers**: `http://localhost:8080/actuator/circuitbreakers`
- **Circuit Events**: `http://localhost:8080/actuator/circuitbreakerevents`
- **Metrics**: `http://localhost:8080/actuator/metrics`

## Configuration

### Circuit Breaker Settings
```yaml
resilience4j:
  circuitbreaker:
    configs:
      default:
        slidingWindowSize: 100
        minimumNumberOfCalls: 10
        failureRateThreshold: 50
        waitDurationInOpenState: 60s
        permittedNumberOfCallsInHalfOpenState: 3
```

## Project Structure

```
src/main/java/com/pnr/aggregator/
├── Application.java
├── controller/
│   └── BookingController.java
├── service/
│   ├── BookingAggregatorService.java
│   ├── TripService.java
│   ├── BaggageService.java
│   └── TicketService.java
├── config/
│   ├── VertxConfig.java
│   ├── CacheConfig.java
│   ├── MongoDbProperties.java
│   ├── WebConfig.java
│   └── WebSocketConfig.java
├── exception/
│   ├── PNRNotFoundException.java
│   └── ServiceUnavailableException.java
├── util/
│   ├── CircuitBreakerLogger.java
│   ├── DataTypeConverter.java
│   └── EventBusLogger.java
├── websocket/
│   └── PNRWebSocketHandler.java
└── model/
    ├── dto/
    │   ├── BookingResponse.java
    │   ├── FlightDTO.java
    │   └── PassengerDTO.java
    └── entity/
        ├── Trip.java
        ├── Flight.java
        ├── Passenger.java
        ├── Baggage.java
        ├── BaggageAllowance.java
        └── Ticket.java
```

## Future Improvements (TODO)

### Redis Cache Operations Optimization

**Current State:**
The application uses Spring Cache abstraction with synchronous Redis operations:
```java
cache.get(pnr, Trip.class)  // Synchronous Redis access
cache.put(pnr, trip)        // Synchronous Redis write
```

**Issue:**
While Redis operations are extremely fast (< 1ms), they are technically blocking and not 100% aligned with Vert.x event-loop model.

**Proposed Solutions:**

#### **Option 1: Hybrid Approach (Quick Win - Recommended)**
Move cache operations to Vert.x worker threads to keep event loop non-blocking:

```java
private Future<Trip> getTripFallback(String pnr, Exception ex) {
    return vertx.executeBlocking(promise -> {
        Cache cache = cacheManager.getCache("trips");
        if (cache != null) {
            Trip cachedTrip = cache.get(pnr, Trip.class);
            if (cachedTrip != null) {
                cachedTrip.setFromCache(true);
                promise.complete(cachedTrip);
                return;
            }
        }
        promise.fail(new ServiceUnavailableException("No cache available"));
    }, false);
}
```

**Benefits:**
- Simple to implement (minimal code changes)
- No new dependencies required
- Event loop stays non-blocking
- Keeps existing cache configuration

#### **Option 2: Full Reactive Redis (100% Async)**
Replace Spring Cache with Lettuce Reactive Redis client:

**Add Dependencies:**
```xml
<dependency>
    <groupId>io.lettuce</groupId>
    <artifactId>lettuce-core</artifactId>
</dependency>
```

**Create Reactive Redis Configuration:**
```java
@Configuration
public class ReactiveRedisConfig {
    
    @Bean
    public ReactiveRedisTemplate<String, Object> reactiveRedisTemplate(
            ReactiveRedisConnectionFactory factory) {
        
        Jackson2JsonRedisSerializer<Object> serializer = 
            new Jackson2JsonRedisSerializer<>(Object.class);
        
        RedisSerializationContext<String, Object> context = 
            RedisSerializationContext.<String, Object>newSerializationContext()
                .key(StringRedisSerializer.UTF_8)
                .value(serializer)
                .hashKey(StringRedisSerializer.UTF_8)
                .hashValue(serializer)
                .build();
        
        return new ReactiveRedisTemplate<>(factory, context);
    }
}
```

**Replace Cache Operations:**
```java
@Autowired
private ReactiveRedisTemplate<String, Object> reactiveRedis;

// Async cache read
private Future<Trip> getCachedTrip(String pnr) {
    Promise<Trip> promise = Promise.promise();
    
    reactiveRedis.opsForValue()
        .get("trips:" + pnr)
        .timeout(Duration.ofMillis(500))
        .subscribe(
            value -> {
                if (value != null) {
                    promise.complete((Trip) value);
                } else {
                    promise.fail("Cache miss");
                }
            },
            error -> promise.fail(error),
            () -> promise.fail("Cache miss")
        );
    
    return promise.future();
}

// Async cache write (fire and forget)
private Future<Void> cacheTrip(String pnr, Trip trip) {
    Promise<Void> promise = Promise.promise();
    
    reactiveRedis.opsForValue()
        .set("trips:" + pnr, trip, Duration.ofMinutes(10))
        .subscribe(
            success -> promise.complete(),
            error -> {
                log.warn("Failed to cache trip: {}", error.getMessage());
                promise.complete(); // Don't fail main operation
            }
        );
    
    return promise.future();
}
```

**Benefits:**
- Fully non-blocking and reactive
- 100% aligned with Vert.x model
- True async everywhere

---
