package com.pnr.aggregator.service;

import com.pnr.aggregator.exception.PNRNotFoundException;
import com.pnr.aggregator.exception.ServiceUnavailableException;
import com.pnr.aggregator.model.entity.Flight;
import com.pnr.aggregator.model.entity.Passenger;
import com.pnr.aggregator.model.entity.Trip;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * TestCategory: Unit Test
 * 
 * Comprehensive unit tests for TripService
 * Coverage: Circuit breaker patterns, MongoDB operations, caching, error
 * handling
 * RequirementCategorized: Core Requirements (MongoDB Source 1 - Trip
 * Information) & Bonus Requirements (Circuit Breaking)
 */
/**
 * -[@ExtendWith](MockitoExtension.class): Integrates Mockito with JUnit 5.
 * --Enables Mockito annotations like [@Mock], [@InjectMocks], etc.
 * --Initializes mocks before each test method automatically
 * --Validates mock usage after each test (detects unused stubs)
 * --Replaces the legacy [@RunWith](MockitoJUnitRunner.class) from JUnit 4
 * --WithoutIT: [@Mock] and [@InjectMocks] annotations wouldn't work;
 * ---mocks would be null, causing NullPointerException in tests.
 */
/**
 * -[@MockitoSettings](strictness = Strictness.LENIENT): Configures Mockito's
 * strictness level.
 * --LENIENT mode allows unused stubs without warnings
 * --Useful when setting up common test data that's not used in every test
 * --Prevents UnnecessaryStubbingException for valid test scenarios
 * --STRICT_STUBS would fail if stubbed methods aren't called
 * --WithoutIT: Tests with shared setup might fail unnecessarily
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TripServiceTest {

    /**
     * -[@Mock]: Creates a mock instance of MongoClient.
     * --Simulates MongoDB database operations without actual database connection
     * --All methods return default values unless stubbed with when().thenReturn()
     * --Enables testing database queries without MongoDB server running
     * --Used to test async MongoDB findOne operations with handlers
     * --WithoutIT: Would require actual MongoDB instance and connection;
     * ---tests would be slow, require infrastructure, and be harder to control.
     */
    @Mock
    private MongoClient mongoClient;

    /**
     * -[@Mock]: Creates mock for Spring CacheManager.
     * --Simulates cache operations (get, put) for resilience testing
     * --Enables testing fallback to cache when MongoDB fails
     * --Allows verification of cache usage patterns
     * --WithoutIT: Can't test caching behavior without actual cache implementation
     */
    @Mock
    private CacheManager cacheManager;

    /**
     * -[@Mock]: Creates mock for Resilience4j CircuitBreakerRegistry.
     * --Provides access to circuit breaker instances by name
     * --Used to configure circuit breaker for testing different states
     * --Enables testing of circuit breaker patterns without real infrastructure
     * --WithoutIT: Can't test resilience patterns and circuit breaker behavior
     */
    @Mock
    private CircuitBreakerRegistry circuitBreakerRegistry;

    /**
     * -[@Mock]: Creates mock for Resilience4j CircuitBreaker.
     * --Simulates circuit breaker states (OPEN, CLOSED, HALF_OPEN)
     * --Allows testing behavior when circuit is open (service unavailable)
     * --Enables verification of circuit breaker metrics (onSuccess, onError)
     * --WithoutIT: Can't test how service responds to circuit breaker state changes
     */
    @Mock
    private CircuitBreaker circuitBreaker;

    /**
     * -[@Mock]: Creates mock for Spring Cache.
     * --Simulates individual cache operations (get, put)
     * --Enables testing cache hit/miss scenarios
     * --Used to verify cached data is used when MongoDB fails
     * --WithoutIT: Can't verify caching logic without actual cache instance
     */
    @Mock
    private Cache cache;

    /**
     * -[@Mock]: Creates mock for Vert.x instance.
     * --Simulates Vert.x async operations for date/time conversions
     * --Enables testing async blocking operations without actual Vert.x runtime
     * --Used in DataTypeConverter.timestampsToDateLocal for timezone conversions
     * --WithoutIT: Can't test async date conversions without Vert.x instance
     */
    @Mock
    private Vertx vertx;

    /**
     * -[@InjectMocks]: Creates instance and injects [@Mock] dependencies into it.
     * --Creates a real instance of TripService
     * --Automatically injects all [@Mock] objects (mongoClient, cacheManager,
     * circuitBreakerRegistry)
     * --Simulates Spring's dependency injection for testing
     * --Uses constructor, setter, or field injection (in that order)
     * --WithoutIT: Would need manual instantiation like new TripService();
     * ---and manual injection of all mocks, making tests harder to write and
     * maintain.
     */
    @InjectMocks
    private TripService tripService;

    private JsonObject validTripDoc;
    private Trip validTrip;

    @BeforeEach
    void setUp() {
        /*
         * whyCodeAdded: Initialize circuit breaker, cache, and test data for
         * TripService tests
         * Sets up circuit breaker in CLOSED state and creates comprehensive trip
         * documents
         * with passengers and flights to test MongoDB operations, caching, and circuit
         * breaker patterns
         */
        // Mock circuit breaker registry
        when(circuitBreakerRegistry.circuitBreaker("tripServiceCB")).thenReturn(circuitBreaker);
        when(circuitBreaker.getName()).thenReturn("tripServiceCB");
        when(circuitBreaker.getState()).thenReturn(CircuitBreaker.State.CLOSED);
        when(circuitBreaker.tryAcquirePermission()).thenReturn(true); // Default: circuit is closed

        // Mock cache manager
        when(cacheManager.getCache(anyString())).thenReturn(cache);

        // Mock vertx.executeBlocking for async timestamp parsing
        // This is critical for mapToTrip() to work correctly
        doAnswer(invocation -> {
            Handler<Promise<Object>> blockingCodeHandler = invocation.getArgument(0);
            Handler<AsyncResult<Object>> resultHandler = invocation.getArgument(1);

            // Execute the blocking code synchronously
            Promise<Object> blockingPromise = Promise.promise();
            try {
                blockingCodeHandler.handle(blockingPromise);
                // If the blocking code completed successfully, notify the result handler
                if (blockingPromise.future().succeeded()) {
                    AsyncResult<Object> result = mock(AsyncResult.class);
                    when(result.succeeded()).thenReturn(true);
                    when(result.result()).thenReturn(blockingPromise.future().result());
                    resultHandler.handle(result);
                } else if (blockingPromise.future().failed()) {
                    AsyncResult<Object> result = mock(AsyncResult.class);
                    when(result.succeeded()).thenReturn(false);
                    when(result.cause()).thenReturn(blockingPromise.future().cause());
                    resultHandler.handle(result);
                }
            } catch (Exception e) {
                AsyncResult<Object> result = mock(AsyncResult.class);
                when(result.succeeded()).thenReturn(false);
                when(result.cause()).thenReturn(e);
                resultHandler.handle(result);
            }
            return null;
        }).when(vertx).executeBlocking(any(Handler.class), any(Handler.class));

        // Initialize the service
        tripService.init();

        // Create valid test data with future dates (upcoming flights)
        validTripDoc = new JsonObject()
                .put("bookingReference", "ABC123")
                .put("cabinClass", "ECONOMY")
                .put("passengers", new JsonArray()
                        .add(new JsonObject()
                                .put("firstName", "John")
                                .put("middleName", "M")
                                .put("lastName", "Doe")
                                .put("passengerNumber", 1)
                                .put("customerId", "C12345")
                                .put("seat", "12A")))
                .put("flights", new JsonArray()
                        .add(new JsonObject()
                                .put("flightNumber", "AA100")
                                .put("departureAirport", "JFK")
                                .put("departureTimeStamp", "2026-12-01T10:00:00Z")
                                .put("arrivalAirport", "LAX")
                                .put("arrivalTimeStamp", "2026-12-01T14:00:00Z")));

        validTrip = new Trip();
        validTrip.setBookingReference("ABC123");
        validTrip.setCabinClass("ECONOMY");
    }

    /**
     * Input: PNR "ABC123" with valid trip data in MongoDB
     * ExpectedOut: Succeeded Future with Trip object containing booking reference,
     * cabin class, passengers, and flights; not from cache
     */
    @Test
    void testGetTripInfo_Success() {
        // Given
        when(circuitBreaker.tryAcquirePermission()).thenReturn(true);
        when(cacheManager.getCache("trips")).thenReturn(cache);

        // Mock MongoDB success
        doAnswer(invocation -> {
            Handler<AsyncResult<JsonObject>> handler = invocation.getArgument(3);
            AsyncResult<JsonObject> result = mock(AsyncResult.class);
            when(result.succeeded()).thenReturn(true);
            when(result.result()).thenReturn(validTripDoc);
            handler.handle(result);
            return null;
        }).when(mongoClient).findOne(eq("trips"), any(JsonObject.class), isNull(), any());

        // When
        Future<Trip> future = tripService.getTripInfo("ABC123");

        // Then
        assertTrue(future.succeeded());
        Trip trip = future.result();
        assertNotNull(trip);
        assertEquals("ABC123", trip.getBookingReference());
        assertEquals("ECONOMY", trip.getCabinClass());
        assertFalse(trip.isFromCache());
        assertEquals(1, trip.getPassengers().size());
        assertEquals("John", trip.getPassengers().get(0).getFirstName());
        assertEquals("C12345", trip.getPassengers().get(0).getCustomerId());

        verify(circuitBreaker).onSuccess(anyLong(), eq(TimeUnit.NANOSECONDS));
        verify(cache).put("ABC123", trip);
    }

    /**
     * Input: PNR "NOTFND" that does not exist in MongoDB
     * ExpectedOut: Failed Future with PNRNotFoundException containing "PNR not
     * found" message
     */
    @Test
    void testGetTripInfo_NotFound() {
        // Given
        when(circuitBreaker.tryAcquirePermission()).thenReturn(true);

        // Mock MongoDB returns null
        doAnswer(invocation -> {
            Handler<AsyncResult<JsonObject>> handler = invocation.getArgument(3);
            AsyncResult<JsonObject> result = mock(AsyncResult.class);
            when(result.succeeded()).thenReturn(true);
            when(result.result()).thenReturn(null);
            handler.handle(result);
            return null;
        }).when(mongoClient).findOne(eq("trips"), any(JsonObject.class), isNull(), any());

        // When
        // Future<Trip> returned even when PNR not found - reactive error handling
        Future<Trip> future = tripService.getTripInfo("NOTFND");

        // Then
        // future.failed() - Checks if Future completed with failure (opposite of
        // succeeded)
        assertTrue(future.failed());
        // future.cause() - Retrieves the exception/throwable that caused the failure
        assertInstanceOf(PNRNotFoundException.class, future.cause());
        assertTrue(future.cause().getMessage().contains("PNR not found"));

        verify(circuitBreaker).onSuccess(anyLong(), eq(TimeUnit.NANOSECONDS));
    }

    /**
     * Input: PNR "ABC123" with MongoDB connection failure but cached data available
     * ExpectedOut: Succeeded Future with Trip from cache, isFromCache=true, with
     * fallback message about cache usage
     */
    @Test
    void testGetTripInfo_MongoDbError_WithCache() {
        // Given
        when(circuitBreaker.tryAcquirePermission()).thenReturn(true);
        when(cacheManager.getCache("trips")).thenReturn(cache);
        when(cache.get("ABC123", Trip.class)).thenReturn(validTrip);

        // Mock MongoDB failure
        doAnswer(invocation -> {
            Handler<AsyncResult<JsonObject>> handler = invocation.getArgument(3);
            AsyncResult<JsonObject> result = mock(AsyncResult.class);
            when(result.succeeded()).thenReturn(false);
            when(result.cause()).thenReturn(new RuntimeException("Connection failed"));
            handler.handle(result);
            return null;
        }).when(mongoClient).findOne(eq("trips"), any(JsonObject.class), isNull(), any());

        // When
        Future<Trip> future = tripService.getTripInfo("ABC123");

        // Then
        assertTrue(future.succeeded());
        Trip trip = future.result();
        assertTrue(trip.isFromCache());
        assertNotNull(trip.getPnrFallbackMsg());
        assertFalse(trip.getPnrFallbackMsg().isEmpty());
        assertTrue(trip.getPnrFallbackMsg().get(0).contains("cache"));

        verify(circuitBreaker).onError(anyLong(), eq(TimeUnit.NANOSECONDS), any());
    }

    /**
     * Input: PNR "ABC123" with MongoDB connection failure and no cached data
     * ExpectedOut: Failed Future with ServiceUnavailableException
     */
    @Test
    void testGetTripInfo_MongoDbError_NoCache() {
        // Given
        when(circuitBreaker.tryAcquirePermission()).thenReturn(true);
        when(cacheManager.getCache("trips")).thenReturn(cache);
        when(cache.get("ABC123", Trip.class)).thenReturn(null);

        // Mock MongoDB failure
        doAnswer(invocation -> {
            Handler<AsyncResult<JsonObject>> handler = invocation.getArgument(3);
            AsyncResult<JsonObject> result = mock(AsyncResult.class);
            when(result.succeeded()).thenReturn(false);
            when(result.cause()).thenReturn(new RuntimeException("Connection failed"));
            handler.handle(result);
            return null;
        }).when(mongoClient).findOne(eq("trips"), any(JsonObject.class), isNull(), any());

        // When
        Future<Trip> future = tripService.getTripInfo("ABC123");

        // Then
        assertTrue(future.failed());
        assertInstanceOf(ServiceUnavailableException.class, future.cause());

        verify(circuitBreaker).onError(anyLong(), eq(TimeUnit.NANOSECONDS), any());
    }

    /**
     * Input: PNR "ABC123" with circuit breaker OPEN state but cached data available
     * ExpectedOut: Succeeded Future with Trip from cache, isFromCache=true, with
     * cache timestamp
     */
    @Test
    void testGetTripInfo_CircuitBreakerOpen_WithCache() {
        // Given
        when(circuitBreaker.tryAcquirePermission()).thenReturn(false);
        when(circuitBreaker.getState()).thenReturn(CircuitBreaker.State.OPEN);
        when(cacheManager.getCache("trips")).thenReturn(cache);
        when(cache.get("ABC123", Trip.class)).thenReturn(validTrip);

        // When
        Future<Trip> future = tripService.getTripInfo("ABC123");

        // Then
        assertTrue(future.succeeded());
        Trip trip = future.result();
        assertTrue(trip.isFromCache());
        assertNotNull(trip.getCacheTimestamp());

        verify(mongoClient, never()).findOne(any(), any(), any(), any());
    }

    /**
     * Input: PNR "ABC123" with circuit breaker OPEN state and no cached data
     * ExpectedOut: Failed Future with ServiceUnavailableException
     */
    @Test
    void testGetTripInfo_CircuitBreakerOpen_NoCache() {
        // Given
        when(circuitBreaker.tryAcquirePermission()).thenReturn(false);
        when(circuitBreaker.getState()).thenReturn(CircuitBreaker.State.OPEN);
        when(cacheManager.getCache("trips")).thenReturn(cache);
        when(cache.get("ABC123", Trip.class)).thenReturn(null);

        // When
        Future<Trip> future = tripService.getTripInfo("ABC123");

        // Then
        assertTrue(future.failed());
        assertInstanceOf(ServiceUnavailableException.class, future.cause());

        verify(mongoClient, never()).findOne(any(), any(), any(), any());
    }

    /**
     * Input: PNR "ABC123" with complete trip data including passenger and flight
     * details
     * ExpectedOut: Trip object with correctly mapped passenger (John M Doe, C12345)
     * and flight (AA100, JFK to LAX)
     */
    @Test
    void testMapToTrip_CompleteData() {
        // When
        Trip trip = tripService.getTripInfo("ABC123").result();

        // Mock successful response
        when(circuitBreaker.tryAcquirePermission()).thenReturn(true);
        doAnswer(invocation -> {
            Handler<AsyncResult<JsonObject>> handler = invocation.getArgument(3);
            AsyncResult<JsonObject> result = mock(AsyncResult.class);
            when(result.succeeded()).thenReturn(true);
            when(result.result()).thenReturn(validTripDoc);
            handler.handle(result);
            return null;
        }).when(mongoClient).findOne(any(), any(), any(), any());

        Future<Trip> future = tripService.getTripInfo("ABC123");
        assertTrue(future.succeeded());
        trip = future.result();

        // Then - Verify passenger mapping
        assertEquals(1, trip.getPassengers().size());
        Passenger passenger = trip.getPassengers().get(0);
        assertEquals("John", passenger.getFirstName());
        assertEquals("M", passenger.getMiddleName());
        assertEquals("Doe", passenger.getLastName());
        assertEquals(1, passenger.getPassengerNumber());
        assertEquals("C12345", passenger.getCustomerId());
        assertEquals("12A", passenger.getSeat());

        // Then - Verify flight mapping
        assertEquals(1, trip.getFlights().size());
        Flight flight = trip.getFlights().get(0);
        assertEquals("AA100", flight.getFlightNumber());
        assertEquals("JFK", flight.getDepartureAirport());
        assertEquals("LAX", flight.getArrivalAirport());
    }

    /**
     * Input: PNR "ABC123"
     * ExpectedOut: MongoDB query with single field "bookingReference":"ABC123"
     * (parameterized for NoSQL injection prevention)
     */
    @Test
    void testGetTripInfo_VerifyQueryFormat() {
        // Given
        when(circuitBreaker.tryAcquirePermission()).thenReturn(true);

        ArgumentCaptor<JsonObject> queryCaptor = ArgumentCaptor.forClass(JsonObject.class);

        doAnswer(invocation -> {
            Handler<AsyncResult<JsonObject>> handler = invocation.getArgument(3);
            AsyncResult<JsonObject> result = mock(AsyncResult.class);
            when(result.succeeded()).thenReturn(true);
            when(result.result()).thenReturn(validTripDoc);
            handler.handle(result);
            return null;
        }).when(mongoClient).findOne(eq("trips"), queryCaptor.capture(), isNull(), any());

        // When
        tripService.getTripInfo("ABC123");

        // Then - Verify MongoDB query structure (NoSQL injection prevention)
        JsonObject capturedQuery = queryCaptor.getValue();
        assertEquals("ABC123", capturedQuery.getString("bookingReference"));
        assertEquals(1, capturedQuery.size()); // Only one field, properly parameterized
    }

    /**
     * Input: PNR "ABC123" with two passengers (John M Doe and Jane K Smith)
     * ExpectedOut: Trip object with 2 passengers, second passenger has customerId
     * "C67890"
     */
    @Test
    void testGetTripInfo_MultiplePassengers() {
        // Given
        JsonObject multiPassengerDoc = validTripDoc.copy();
        multiPassengerDoc.getJsonArray("passengers")
                .add(new JsonObject()
                        .put("firstName", "Jane")
                        .put("middleName", "K")
                        .put("lastName", "Smith")
                        .put("passengerNumber", 2)
                        .put("customerId", "C67890")
                        .put("seat", "12B"));

        when(circuitBreaker.tryAcquirePermission()).thenReturn(true);

        doAnswer(invocation -> {
            Handler<AsyncResult<JsonObject>> handler = invocation.getArgument(3);
            AsyncResult<JsonObject> result = mock(AsyncResult.class);
            when(result.succeeded()).thenReturn(true);
            when(result.result()).thenReturn(multiPassengerDoc);
            handler.handle(result);
            return null;
        }).when(mongoClient).findOne(any(), any(), any(), any());

        // When
        Future<Trip> future = tripService.getTripInfo("ABC123");

        // Then
        assertTrue(future.succeeded());
        Trip trip = future.result();
        assertEquals(2, trip.getPassengers().size());
        assertEquals("C67890", trip.getPassengers().get(1).getCustomerId());
    }

    /**
     * Input: Customer ID "C12345" with 2 trips in MongoDB
     * ExpectedOut: Succeeded Future with List of 2 Trips with booking references
     * "ABC123" and "XYZ789"
     */
    @Test
    void testGetTripsByCustomerId_Success() {
        // Given
        String customerId = "C12345";
        JsonArray results = new JsonArray()
                .add(validTripDoc)
                .add(validTripDoc.copy().put("bookingReference", "XYZ789"));

        doAnswer(invocation -> {
            Handler<AsyncResult<List<JsonObject>>> handler = invocation.getArgument(2);
            AsyncResult<List<JsonObject>> result = mock(AsyncResult.class);
            when(result.succeeded()).thenReturn(true);
            List<JsonObject> resultList = new ArrayList<>();
            for (int i = 0; i < results.size(); i++) {
                resultList.add(results.getJsonObject(i));
            }
            when(result.result()).thenReturn(resultList);
            handler.handle(result);
            return null;
        }).when(mongoClient).find(eq("trips"), any(JsonObject.class), any());

        // When
        Future<List<Trip>> future = tripService.getTripsByCustomerId(customerId);

        // Then
        assertTrue(future.succeeded());
        List<Trip> trips = future.result();
        assertEquals(2, trips.size());
        assertEquals("ABC123", trips.get(0).getBookingReference());
        assertEquals("XYZ789", trips.get(1).getBookingReference());
    }

    /**
     * Input: Customer ID "C99999" (non-existent)
     * ExpectedOut: Succeeded Future with empty List of Trips
     */
    @Test
    void testGetTripsByCustomerId_NoResults() {
        // Given
        String customerId = "C99999";

        doAnswer(invocation -> {
            Handler<AsyncResult<List<JsonObject>>> handler = invocation.getArgument(2);
            AsyncResult<List<JsonObject>> result = mock(AsyncResult.class);
            when(result.succeeded()).thenReturn(true);
            when(result.result()).thenReturn(List.of());
            handler.handle(result);
            return null;
        }).when(mongoClient).find(eq("trips"), any(JsonObject.class), any());

        // When
        Future<List<Trip>> future = tripService.getTripsByCustomerId(customerId);

        // Then
        assertTrue(future.succeeded());
        List<Trip> trips = future.result();
        assertTrue(trips.isEmpty());
    }

    /**
     * Input: Customer ID "C12345" with MongoDB connection failure
     * ExpectedOut: Failed Future with error message "Connection failed"
     */
    @Test
    void testGetTripsByCustomerId_MongoDbError() {
        // Given
        String customerId = "C12345";

        // Mock cache to return null (no cached data available)
        when(cache.get(eq(customerId), eq(List.class))).thenReturn(null);

        doAnswer(invocation -> {
            Handler<AsyncResult<List<JsonObject>>> handler = invocation.getArgument(2);
            AsyncResult<List<JsonObject>> result = mock(AsyncResult.class);
            when(result.succeeded()).thenReturn(false);
            when(result.cause()).thenReturn(new RuntimeException("Connection failed"));
            handler.handle(result);
            return null;
        }).when(mongoClient).find(eq("trips"), any(JsonObject.class), any());

        // When
        Future<List<Trip>> future = tripService.getTripsByCustomerId(customerId);

        // Then
        assertTrue(future.failed());
        assertTrue(future.cause().getMessage().contains("Connection failed") ||
                future.cause().getMessage().contains("temporarily unavailable"));
    }

    /**
     * Input: Customer ID "C12345"
     * ExpectedOut: MongoDB query with dot notation field
     * "passengers.customerId":"C12345" (parameterized)
     */
    @Test
    void testGetTripsByCustomerId_VerifyQueryFormat() {
        // Given
        String customerId = "C12345";
        ArgumentCaptor<JsonObject> queryCaptor = ArgumentCaptor.forClass(JsonObject.class);

        doAnswer(invocation -> {
            Handler<AsyncResult<List<JsonObject>>> handler = invocation.getArgument(2);
            AsyncResult<List<JsonObject>> result = mock(AsyncResult.class);
            when(result.succeeded()).thenReturn(true);
            when(result.result()).thenReturn(List.of());
            handler.handle(result);
            return null;
        }).when(mongoClient).find(eq("trips"), queryCaptor.capture(), any());

        // When
        tripService.getTripsByCustomerId(customerId);

        // Then - Verify MongoDB query uses dot notation for nested field
        JsonObject capturedQuery = queryCaptor.getValue();
        assertEquals("C12345", capturedQuery.getString("passengers.customerId"));
        assertEquals(1, capturedQuery.size()); // Only one field, properly parameterized
    }

    /**
     * Input: Customer ID "C12345" with 5 trips in MongoDB
     * ExpectedOut: Succeeded Future with List of 5 Trips (PNR0 to PNR4)
     */
    @Test
    void testGetTripsByCustomerId_MultipleTrips() {
        // Given
        String customerId = "C12345";
        JsonArray results = new JsonArray();
        for (int i = 0; i < 5; i++) {
            JsonObject trip = validTripDoc.copy();
            trip.put("bookingReference", "PNR" + i);
            results.add(trip);
        }

        doAnswer(invocation -> {
            Handler<AsyncResult<List<JsonObject>>> handler = invocation.getArgument(2);
            AsyncResult<List<JsonObject>> result = mock(AsyncResult.class);
            when(result.succeeded()).thenReturn(true);
            List<JsonObject> resultList = new ArrayList<>();
            for (int i = 0; i < results.size(); i++) {
                resultList.add(results.getJsonObject(i));
            }
            when(result.result()).thenReturn(resultList);
            handler.handle(result);
            return null;
        }).when(mongoClient).find(eq("trips"), any(JsonObject.class), any());

        // When
        Future<List<Trip>> future = tripService.getTripsByCustomerId(customerId);

        // Then
        assertTrue(future.succeeded());
        assertEquals(5, future.result().size());
    }
}
