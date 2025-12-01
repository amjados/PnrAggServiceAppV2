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
 * Comprehensive unit tests for TripService
 * Coverage: Circuit breaker patterns, MongoDB operations, caching, error
 * handling
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TripServiceTest {

    @Mock
    private MongoClient mongoClient;

    @Mock
    private CacheManager cacheManager;

    @Mock
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @Mock
    private CircuitBreaker circuitBreaker;

    @Mock
    private Cache cache;

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

        // Initialize the service
        tripService.init();

        // Create valid test data
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
                                .put("departureTimeStamp", "2025-12-01T10:00:00Z")
                                .put("arrivalAirport", "LAX")
                                .put("arrivalTimeStamp", "2025-12-01T14:00:00Z")));

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
        Future<Trip> future = tripService.getTripInfo("NOTFND");

        // Then
        assertTrue(future.failed());
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
        assertTrue(future.cause().getMessage().contains("Connection failed"));
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
