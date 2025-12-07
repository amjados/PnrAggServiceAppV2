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

## TODO-TEST: Missing Test Types

### Current Test Coverage
- ✅ **Unit Tests**: 70 tests across service, controller, exception layers
- ✅ **Component Tests**: 17 tests for service orchestration (BookingAggregatorService)
- **Total**: 87 test methods

### Missing Test Types (To Be Implemented)

#### 1. Integration Tests (High Priority)
**Purpose**: Test with real MongoDB using `init-mongo.js` seed data

**Test Data Available in `init-mongo.js`:**
- PNRs: `GHTW42`, `ABC123`, `XYZ789`, `DEF456`, `PQR999`, `GHR001`, `GHR002`
- Customer IDs for multi-booking tests: `1099` (appears in GHR001 and GHR002)
- Special cases: GHTW42 passenger 1 has NO ticket (tests missing data scenarios)

**Example Tests Needed:**
```java
@SpringBootTest
@DirtiesContext
class TripServiceIntegrationTest {
    @Autowired
    private TripService tripService;
    
    @Test
    void testGetTrip_WithRealMongoDB() {
        // Uses actual GHTW42 from init-mongo.js
        Trip trip = tripService.getTripInfo("GHTW42").result();
        assertEquals("1216", trip.getPassengers().get(1).getCustomerId());
    }
    
    @Test
    void testCustomerId1099_HasMultipleBookings() {
        // Customer 1099 has bookings in GHR001 and GHR002
        List<Trip> trips = tripService.getTripsByCustomerId("1099").result();
        assertEquals(2, trips.size());
    }
}
```

**Files to Create:**
- `TripServiceIntegrationTest.java`
- `BaggageServiceIntegrationTest.java`
- `TicketServiceIntegrationTest.java`
- `BookingAggregatorIntegrationTest.java`

#### 2. End-to-End (E2E) Tests (High Priority)
**Purpose**: Test complete REST API flow with real HTTP requests

**Example:**
```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BookingE2ETest {
    @Autowired
    private TestRestTemplate restTemplate;
    
    @Test
    void testGetBooking_CompleteFlow() {
        ResponseEntity<BookingResponse> response = 
            restTemplate.getForEntity("/booking/ABC123", BookingResponse.class);
        
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("ABC123", response.getBody().getPnr());
        assertEquals("BUSINESS", response.getBody().getCabinClass());
    }
    
    @Test
    void testGetByCustomerId_1099_ReturnsMultipleBookings() {
        // Customer 1099 in seed data has 2 bookings
        ResponseEntity<BookingResponse[]> response = 
            restTemplate.getForEntity("/booking/customer/1099", BookingResponse[].class);
        
        assertEquals(2, response.getBody().length);
    }
}
```

**Files to Create:**
- `BookingE2ETest.java`
- `CustomerIdEndpointE2ETest.java`

#### 3. Repository/Database Tests (Medium Priority)
**Purpose**: Test MongoDB queries, indexes, and aggregations

**Example:**
```java
@DataMongoTest
class TripRepositoryTest {
    @Autowired
    private MongoTemplate mongoTemplate;
    
    @Test
    void testFindByBookingReference() {
        Trip trip = mongoTemplate.findById("ABC123", Trip.class, "trips");
        assertNotNull(trip);
        assertEquals("BUSINESS", trip.getCabinClass());
    }
    
    @Test
    void testFindByCustomerId_MultipleBookings() {
        // Find all trips for customer 1099
        Query query = new Query(Criteria.where("passengers.customerId").is("1099"));
        List<Trip> trips = mongoTemplate.find(query, Trip.class, "trips");
        assertEquals(2, trips.size());
    }
}
```

**Files to Create:**
- `TripRepositoryTest.java`
- `BaggageRepositoryTest.java`
- `TicketRepositoryTest.java`

#### 4. WebSocket Integration Tests (Medium Priority)
**Purpose**: Test real-time WebSocket connections and message flow

**Example:**
```java
@SpringBootTest(webEnvironment = RANDOM_PORT)
class WebSocketIntegrationTest {
    @Test
    void testWebSocketConnection_ReceivesBookingUpdates() {
        WebSocketStompClient stompClient = new WebSocketStompClient(
            new SockJsClient(createTransportClient())
        );
        
        StompSession session = stompClient.connect(wsUrl, handler).get(1, SECONDS);
        session.subscribe("/topic/bookings", new BookingFrameHandler());
        
        // Trigger booking fetch
        // Verify WebSocket message received
    }
}
```

**Files to Create:**
- `WebSocketIntegrationTest.java`
- `PNRWebSocketHandlerIntegrationTest.java`

#### 5. Circuit Breaker Integration Tests (Medium Priority)
**Purpose**: Test circuit breaker behavior under actual failure conditions

**Example:**
```java
@SpringBootTest
class CircuitBreakerIntegrationTest {
    @Autowired
    private TripService tripService;
    
    @Autowired
    private CircuitBreakerRegistry registry;
    
    @Test
    void testCircuitBreaker_OpensAfterFailures() {
        CircuitBreaker cb = registry.circuitBreaker("tripServiceCB");
        
        // Simulate failures to open circuit
        for (int i = 0; i < 10; i++) {
            // Cause service failures
        }
        
        assertEquals(CircuitBreaker.State.OPEN, cb.getState());
    }
}
```

**Files to Create:**
- `CircuitBreakerIntegrationTest.java`

#### 6. Performance/Load Tests (Low Priority)
**Purpose**: Verify system behavior under concurrent load

**Example:**
```java
class BookingPerformanceTest {
    @Test
    void testConcurrent100Requests_CompletesIn5Seconds() {
        ExecutorService executor = Executors.newFixedThreadPool(100);
        long startTime = System.currentTimeMillis();
        
        // Submit 100 concurrent booking requests
        List<CompletableFuture<ResponseEntity<?>>> futures = ...;
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        
        long duration = System.currentTimeMillis() - startTime;
        assertTrue(duration < 5000, "All requests should complete within 5 seconds");
    }
}
```

**Files to Create:**
- `BookingPerformanceTest.java`

#### 7. Contract Tests (Low Priority)
**Purpose**: Verify API contract compatibility and schema validation

**Tools**: Spring Cloud Contract or Pact
**Files to Create:**
- `BookingApiContractTest.java`
- Contract definition files

#### 8. Mutation Tests (Low Priority)
**Purpose**: Verify test quality by injecting code mutations

**Tool**: PIT (Pitest)
**Configuration**: Add pitest-maven plugin to `pom.xml`

---

### Test Data Alignment Notes

**Seed Data from `init-mongo.js`:**
```javascript
// PNR with passenger missing ticket (test edge case)
GHTW42: passenger 1 (customerId: null), passenger 2 (customerId: "1216")
        - Only passenger 2 has ticket

// Standard business booking
ABC123: passenger 1 (customerId: "5678"), BUSINESS class

// First class with 2 passengers
XYZ789: passengers (customerIds: "9012", "9013"), FIRST class

// Economy with 3 passengers
DEF456: passengers (customerIds: null, "3456", "3457")
        - Passenger 1 has no ticket

// Premium economy with 2 flights
PQR999: passenger (customerId: "7890"), PREMIUM_ECONOMY

// Multi-booking customer (IMPORTANT FOR TESTS)
Customer "1099" appears in:
  - GHR001: passenger 1
  - GHR002: passenger 1
(Use this for testing customer ID endpoint)
```

**Integration Test Priority:**
1. Test PNR `GHTW42` - validates missing ticket handling
2. Test Customer ID `1099` - validates multiple bookings
3. Test PNR `ABC123` - standard happy path
4. Test PNR `DEF456` - multiple passengers with mixed data

---

### Implementation Checklist

- [ ] Create integration test base class with MongoDB test container
- [ ] Implement TripServiceIntegrationTest with GHTW42, ABC123 test cases
- [ ] Implement BookingAggregatorIntegrationTest with customer 1099 multi-booking test
- [ ] Implement BookingE2ETest with REST API validation
- [ ] Implement WebSocketIntegrationTest with real connection
- [ ] Implement CircuitBreakerIntegrationTest with failure scenarios
- [ ] Add Repository tests for custom MongoDB queries
- [ ] Configure Pitest for mutation testing
- [ ] Document performance benchmarks

---

## Test Statistics

| Metric | Count |
|--------|-------|
| Total Test Files | 8 |
| Total Test Methods | 87 |
| Unit Tests | 70 |
| Component Tests | 17 |
| Integration Tests | 0 (documented as TODO) |
| E2E Tests | 0 (documented as TODO) |

**Test Coverage Summary:**
- ✅ **Unit Tests**: Complete coverage across all layers (controller, service, exception)
- ✅ **Component Tests**: Service orchestration testing (BookingAggregatorService)
- ❌ **Integration Tests**: Not yet implemented (see TODO-TEST section above)
- ❌ **E2E Tests**: Not yet implemented (see TODO-TEST section above)

**Test Distribution:**
- Controller Tests: 21 methods (BookingController: 11, CustomerIdEndpoint: 10)
- Service Tests: 57 methods (Aggregator: 17, Trip: 14, Baggage: 8, Ticket: 9, WebSocket: 9)
- Exception Tests: 9 methods

---

## TODO-TEST-2: Advanced JUnit 5 Test Types

### Missing Advanced Test Types

#### 1. @ParameterizedTest ⭐ (Most Important - **PARTIALLY IMPLEMENTED**)
**Run the same test with multiple inputs using various data sources**

**Available Annotations:**
- `@ValueSource` - Simple values (strings, ints, booleans, etc.)
- `@CsvSource` - CSV data with multiple parameters
- `@MethodSource` - Complex objects from a method
- `@EnumSource` - Test all enum values

**✅ Implemented Example (CircuitBreakerIntegrationTest.java):**
```java
@ParameterizedTest
@ValueSource(strings = {"GHTW42", "ABC123"})
void testPhase1_MongoUp_ExpectSuccess(String pnr) {
    // Test runs twice: once for GHTW42, once for ABC123
}
```

**Use Cases for Your Project:**
- ✅ **Circuit Breaker Tests**: Test multiple PNRs (`GHTW42`, `ABC123`) - IMPLEMENTED
- ❌ Test multiple PNR formats (valid/invalid)
- ❌ Test different HTTP status codes
- ❌ Test various error messages
- ❌ Test different cabin classes (ECONOMY, BUSINESS, FIRST)

**Example Tests Needed:**
```java
// Test multiple cabin classes
@ParameterizedTest
@ValueSource(strings = {"ECONOMY", "BUSINESS", "FIRST"})
void testGetBooking_DifferentCabinClasses(String cabinClass) {
    validTrip.setCabinClass(cabinClass);
    // Test logic...
}

// Test multiple parameters with CSV
@ParameterizedTest
@CsvSource({
    "GHTW42, ECONOMY, 200",
    "ABC123, BUSINESS, 200",
    "INVALID, ECONOMY, 404"
})
void testGetBooking_VariousScenarios(String pnr, String cabin, int expectedStatus) {
    // Test logic...
}

// Test with MethodSource for complex objects
@ParameterizedTest
@MethodSource("provideInvalidPnrs")
void testGetBooking_InvalidPnrFormats(String pnr) {
    // Test validation logic...
}

static Stream<String> provideInvalidPnrs() {
    return Stream.of("", "A", "TOOLONG123", "ABC", "12345");
}
```

---

#### 2. @RepeatedTest ❌ (Not Implemented)
**Run the same test multiple times to catch flaky behavior**

**Use Cases:**
- Test async operations for race conditions
- Verify caching consistency
- Test reactive Future completion reliability

**Example Tests Needed:**
```java
@RepeatedTest(10)
@DisplayName("Verify async aggregation stability (10 iterations)")
void testAggregateBooking_AsyncStability() {
    Future<BookingResponse> future = aggregatorService.aggregateBooking("GHTW42");
    assertTrue(future.succeeded(), "Async operation should always succeed");
}

@RepeatedTest(value = 5, name = "Cache consistency test {currentRepetition}/{totalRepetitions}")
void testCacheConsistency() {
    // Test cache returns same data across multiple calls
}
```

---

#### 3. @TestFactory ❌ (Not Implemented)
**Generate tests dynamically at runtime**

**Use Cases:**
- Test all PNRs from a data source
- Generate tests for each passenger in booking
- Test each flight in an itinerary

**Example Tests Needed:**
```java
@TestFactory
Stream<DynamicTest> testAllPnrsFromDatabase() {
    List<String> pnrs = Arrays.asList("GHTW42", "ABC123", "XYZ789", "DEF456");
    return pnrs.stream()
        .map(pnr -> dynamicTest("Test PNR: " + pnr, () -> {
            Future<BookingResponse> future = aggregatorService.aggregateBooking(pnr);
            assertTrue(future.succeeded(), "PNR " + pnr + " should succeed");
        }));
}

@TestFactory
Collection<DynamicTest> testEachPassengerInBooking() {
    // Generate test for each passenger dynamically
}
```

---

#### 4. @Nested ❌ (Not Implemented)
**Organize tests in inner classes for better structure**

**Use Cases:**
- Group success vs. error tests
- Separate validation vs. integration tests
- Organize by feature (trip, baggage, ticket)

**Example Tests Needed:**
```java
class BookingAggregatorServiceTest {
    
    @Nested
    @DisplayName("Success Scenarios")
    class SuccessTests {
        @Test void testFullDataAvailable() { }
        @Test void testPartialData() { }
    }
    
    @Nested
    @DisplayName("Error Scenarios")
    class ErrorTests {
        @Test void testMongoDbDown() { }
        @Test void testServiceUnavailable() { }
    }
    
    @Nested
    @DisplayName("Fallback Scenarios")
    class FallbackTests {
        @Test void testCachedData() { }
        @Test void testDefaultBaggage() { }
    }
}
```

---

#### 5. @Timeout ❌ (Not Implemented)
**Enforce maximum execution time for tests**

**Use Cases:**
- Test async operations complete quickly
- Verify circuit breaker timeout
- Test performance requirements

**Example Tests Needed:**
```java
@Test
@Timeout(value = 2, unit = TimeUnit.SECONDS)
void testAggregateBooking_FastResponse() {
    Future<BookingResponse> future = aggregatorService.aggregateBooking("GHTW42");
    assertTrue(future.succeeded(), "Should complete within 2 seconds");
}

@Test
@Timeout(value = 500, unit = TimeUnit.MILLISECONDS)
void testCircuitBreaker_FastFallback() {
    // Verify degraded response < 500ms when circuit is open
}
```

---

#### 6. @TestMethodOrder / @Order ✅ (IMPLEMENTED)
**Control test execution order**

**✅ Implemented In: CircuitBreakerIntegrationTest.java**
```java
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CircuitBreakerIntegrationTest {
    @Test @Order(1) void testPhase1_MongoUp() { }
    @Test @Order(2) void testPhase2_MongoDown() { }
    @Test @Order(3) void testPhase3_Recovery() { }
}
```

**Use Cases:**
- ✅ Integration tests with dependencies (Circuit Breaker phases) - IMPLEMENTED
- Setup → Test → Cleanup sequences

---

#### 7. @TestInstance(Lifecycle.PER_CLASS) ✅ (IMPLEMENTED)
**Share state across all tests in a class**

**✅ Implemented In: CircuitBreakerIntegrationTest.java**
```java
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CircuitBreakerIntegrationTest {
    private List<BookingResponse> testResults; // Shared across all tests
    
    @BeforeAll
    void setUpAll() {
        testResults = new ArrayList<>();
    }
}
```

---

### Summary: Test Type Implementation Status

| Test Type | Status | Use Case | Priority |
|-----------|--------|----------|----------|
| `@Test` | ✅ Yes | Standard tests | - |
| `@BeforeEach` | ✅ Yes | Setup per test | - |
| `@ExtendWith` | ✅ Yes | Mockito integration | - |
| `@EnabledIf/DisabledIf` | ✅ Yes | Conditional execution | - |
| `@ParameterizedTest` | 🟡 Partial | Multiple inputs | ⭐ High |
| `@RepeatedTest` | ❌ Missing | Stability testing | ⭐ High |
| `@TestFactory` | ❌ Missing | Dynamic tests | Medium |
| `@Nested` | ❌ Missing | Test organization | ⭐ High |
| `@Timeout` | ❌ Missing | Performance limits | ⭐ High |
| `@TestMethodOrder` | ✅ Yes | Execution order | - |
| `@TestInstance` | ✅ Yes | Shared state | - |
| `@Disabled` | ❌ Missing | Skip tests | Low |

**Legend:**
- ✅ Fully Implemented
- 🟡 Partially Implemented
- ❌ Not Implemented
- ⭐ High Priority for Implementation

---

### Most Valuable for This Project (Priority Order)

1. **@ParameterizedTest** ⭐⭐⭐
   - Test multiple PNRs, cabin classes, error scenarios
   - Already started with Circuit Breaker tests
   - Expand to controller and service tests

2. **@Nested** ⭐⭐⭐
   - Better organize your 87+ test methods
   - Group success/error/fallback scenarios
   - Improve test readability and maintainability

3. **@RepeatedTest** ⭐⭐
   - Test async/reactive reliability
   - Verify Vert.x Future consistency
   - Catch flaky test behavior

4. **@Timeout** ⭐⭐
   - Ensure performance SLAs (< 1s for degraded responses)
   - Validate circuit breaker fast-fail
   - Test async operation timeouts

---

### Implementation Checklist

**Phase 1: Expand Parameterized Tests** (High Priority)
- [ ] Add `@ParameterizedTest` to `BookingControllerTest` for multiple PNRs
- [ ] Add `@CsvSource` tests for cabin classes (ECONOMY, BUSINESS, FIRST)
- [ ] Add `@MethodSource` tests for invalid PNR formats
- [ ] Add `@EnumSource` tests if enum values exist (e.g., BookingStatus)

**Phase 2: Add Test Organization** (High Priority)
- [ ] Refactor `BookingAggregatorServiceTest` with `@Nested` classes
- [ ] Organize success/error/fallback scenarios
- [ ] Group trip/baggage/ticket feature tests

**Phase 3: Add Reliability Tests** (Medium Priority)
- [ ] Add `@RepeatedTest` for async operations
- [ ] Add `@Timeout` for performance-critical tests
- [ ] Test cache consistency with repeated calls

**Phase 4: Dynamic Test Generation** (Low Priority)
- [ ] Add `@TestFactory` for all PNRs from test data
- [ ] Generate passenger tests dynamically
- [ ] Generate flight itinerary tests

---
