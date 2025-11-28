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

### Prerequisites
- Java 21+
- Docker & Docker Compose
- Maven 3.9+

### Run the Application

1. **Start infrastructure**
   ```bash
   docker-compose up -d
   ```

2. **Build and run**
   ```bash
   mvn clean install
   mvn spring-boot:run
   ```

3. **Test the endpoint**
   ```bash
   curl http://localhost:8080/booking/GHTW42
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

### Manual Test

```bash
# Stop MongoDB to simulate failure
docker-compose stop mongodb

# Make requests to trigger circuit breaker
for i in {1..15}; do
  curl http://localhost:8080/booking/GHTW42
  sleep 1
done

# Restart MongoDB
docker-compose start mongodb
```

**Expected behavior:**
- Initial requests fail slowly (database timeout)
- Circuit opens after failure threshold
- Subsequent requests fail fast with fallback data
- Circuit recovers after MongoDB restart

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
│   └── CircuitBreakerLogger.java
├── exception/
│   ├── PNRNotFoundException.java
│   └── ServiceUnavailableException.java
└── model/
    ├── dto/
    └── entity/
```

## Technology Stack

- **Java 21** - Programming language
- **Spring Boot 3.2** - Application framework
- **Vert.x 4.5** - Reactive toolkit
- **Resilience4j 2.1** - Circuit breaker
- **MongoDB 7.0** - Database
- **Redis 7** - Cache
- **Maven 3.9** - Build tool
- **Docker** - Containerization
