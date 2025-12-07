package com.pnr.aggregator.service;

import com.pnr.aggregator.model.entity.Baggage;
import com.pnr.aggregator.model.entity.BaggageAllowance;
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

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * TestCategory: Unit Test
 * 
 * Comprehensive unit tests for BaggageService
 * Coverage: Circuit breaker, fallback with defaults, MongoDB operations
 * RequirementCategorized: Core Requirements (MongoDB Source 2 - Baggage
 * Allowance) & Bonus Requirements (Circuit Breaking)
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
@ExtendWith(MockitoExtension.class)
class BaggageServiceTest {

    /**
     * -[@Mock]: Creates a mock instance of MongoClient.
     * --Simulates MongoDB operations for baggage collection
     * --Enables testing baggage allowance retrieval without database
     * --Allows testing fallback to default values (25kg/7kg)
     * --Used to test async MongoDB findOne operations
     * --WithoutIT: Would require MongoDB instance and baggage test data
     */
    @Mock
    private MongoClient mongoClient;

    /**
     * -[@Mock]: Creates mock for CircuitBreakerRegistry.
     * --Provides circuit breaker instances by name
     * --Enables testing resilience patterns
     * --Allows testing different circuit breaker states
     * --WithoutIT: Can't test circuit breaker integration
     */
    @Mock
    private CircuitBreakerRegistry circuitBreakerRegistry;

    /**
     * -[@Mock]: Creates mock for CircuitBreaker.
     * --Simulates circuit breaker for baggage service
     * --Enables testing behavior when circuit opens
     * --Allows verification of success/error metrics
     * --Important: Missing baggage returns DEFAULT values (not failure)
     * --WithoutIT: Can't test resilience and fallback patterns
     */
    @Mock
    private CircuitBreaker circuitBreaker;

    /**
     * -[@InjectMocks]: Creates instance and injects [@Mock] dependencies into it.
     * --Creates a real instance of BaggageService
     * --Automatically injects [@Mock] objects (mongoClient, circuitBreakerRegistry)
     * --Simulates Spring's dependency injection for testing
     * --Uses constructor, setter, or field injection (in that order)
     * --WithoutIT: Would need manual instantiation like new BaggageService();
     * ---and manual injection of mocks, making tests harder to write.
     */
    @InjectMocks
    private BaggageService baggageService;

    private JsonObject validBaggageDoc;

    @BeforeEach
    void setUp() {
        /*
         * whyCodeAdded: Initialize circuit breaker mocks and test data for
         * BaggageService tests
         * Sets up circuit breaker in CLOSED state to enable testing normal operations,
         * and creates valid baggage document with allowances to test MongoDB retrieval
         * and fallback scenarios
         */
        // Mock circuit breaker registry
        when(circuitBreakerRegistry.circuitBreaker("baggageServiceCB")).thenReturn(circuitBreaker);
        when(circuitBreaker.getName()).thenReturn("baggageServiceCB");
        when(circuitBreaker.getState()).thenReturn(CircuitBreaker.State.CLOSED);

        // Initialize the service
        baggageService.init();

        // Create valid test data
        validBaggageDoc = new JsonObject()
                .put("bookingReference", "ABC123")
                .put("baggageAllowances", new JsonArray()
                        .add(new JsonObject()
                                .put("passengerNumber", 1)
                                .put("allowanceUnit", "kg")
                                .put("checkedAllowanceValue", 30)
                                .put("carryOnAllowanceValue", 10)));
    }

    /**
     * Input: PNR "ABC123" with valid baggage data in MongoDB
     * ExpectedOut: Succeeded Future with Baggage containing 1 allowance (30kg
     * checked, 10kg carry-on), not from cache/default
     */
    @Test
    void testGetBaggageInfo_Success() {
        // Given
        when(circuitBreaker.tryAcquirePermission()).thenReturn(true);

        // Mock MongoDB success
        doAnswer(invocation -> {
            Handler<AsyncResult<JsonObject>> handler = invocation.getArgument(3);
            AsyncResult<JsonObject> result = mock(AsyncResult.class);
            when(result.succeeded()).thenReturn(true);
            when(result.result()).thenReturn(validBaggageDoc);
            handler.handle(result);
            return null;
        }).when(mongoClient).findOne(eq("baggage"), any(JsonObject.class), isNull(), any());

        // When
        Future<Baggage> future = baggageService.getBaggageInfo("ABC123");

        // Then
        assertTrue(future.succeeded());
        Baggage baggage = future.result();
        assertNotNull(baggage);
        assertEquals("ABC123", baggage.getBookingReference());
        assertFalse(baggage.isFromCache());
        assertFalse(baggage.isFromDefault());
        assertEquals(1, baggage.getAllowances().size());

        BaggageAllowance allowance = baggage.getAllowances().get(0);
        assertEquals(1, allowance.getPassengerNumber());
        assertEquals("kg", allowance.getAllowanceUnit());
        assertEquals(30, allowance.getCheckedAllowanceValue());
        assertEquals(10, allowance.getCarryOnAllowanceValue());

        verify(circuitBreaker).onSuccess(anyLong(), eq(TimeUnit.NANOSECONDS));
    }

    /**
     * Input: PNR "ABC123", baggage not found in MongoDB
     * ExpectedOut: Succeeded Future with default Baggage (25kg checked, 7kg
     * carry-on) with "default" fallback message
     */
    @Test
    void testGetBaggageInfo_NotFound_ReturnsDefault() {
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
        }).when(mongoClient).findOne(eq("baggage"), any(JsonObject.class), isNull(), any());

        // When
        // Future<Baggage> - Async operation that returns default baggage when not found
        Future<Baggage> future = baggageService.getBaggageInfo("ABC123");

        // Then
        // future.succeeded() is true even when not found - default baggage is valid
        // fallback
        assertTrue(future.succeeded());
        // future.result() returns Baggage with default values (25kg checked, 7kg
        // carry-on)
        Baggage baggage = future.result();
        assertNotNull(baggage);
        assertTrue(baggage.isFromDefault());
        assertFalse(baggage.isFromCache());

        // Verify default values
        BaggageAllowance defaultAllowance = baggage.getAllowances().get(0);
        assertEquals(25, defaultAllowance.getCheckedAllowanceValue());
        assertEquals(7, defaultAllowance.getCarryOnAllowanceValue());
        assertEquals("kg", defaultAllowance.getAllowanceUnit());

        // Verify fallback messages
        assertNotNull(baggage.getBaggageFallbackMsg());
        assertTrue(baggage.getBaggageFallbackMsg().get(0).contains("default"));

        verify(circuitBreaker).onError(anyLong(), eq(TimeUnit.NANOSECONDS), any());
    }

    /**
     * Input: PNR "ABC123", MongoDB connection failure
     * ExpectedOut: Succeeded Future with default Baggage with fallback message
     */
    @Test
    void testGetBaggageInfo_MongoDbError_ReturnsDefault() {
        // Given
        when(circuitBreaker.tryAcquirePermission()).thenReturn(true);

        // Mock MongoDB failure
        doAnswer(invocation -> {
            Handler<AsyncResult<JsonObject>> handler = invocation.getArgument(3);
            AsyncResult<JsonObject> result = mock(AsyncResult.class);
            when(result.succeeded()).thenReturn(false);
            when(result.cause()).thenReturn(new RuntimeException("Connection failed"));
            handler.handle(result);
            return null;
        }).when(mongoClient).findOne(eq("baggage"), any(JsonObject.class), isNull(), any());

        // When
        Future<Baggage> future = baggageService.getBaggageInfo("ABC123");

        // Then
        assertTrue(future.succeeded());
        Baggage baggage = future.result();
        assertTrue(baggage.isFromDefault());
        assertNotNull(baggage.getBaggageFallbackMsg());

        verify(circuitBreaker).onError(anyLong(), eq(TimeUnit.NANOSECONDS), any());
    }

    /**
     * Input: PNR "ABC123", circuit breaker OPEN state
     * ExpectedOut: Succeeded Future with default Baggage with fallback messages,
     * MongoDB not called
     */
    @Test
    void testGetBaggageInfo_CircuitBreakerOpen_ReturnsDefault() {
        // Given
        when(circuitBreaker.tryAcquirePermission()).thenReturn(false);
        when(circuitBreaker.getState()).thenReturn(CircuitBreaker.State.OPEN);

        // When
        Future<Baggage> future = baggageService.getBaggageInfo("ABC123");

        // Then
        assertTrue(future.succeeded());
        Baggage baggage = future.result();
        assertTrue(baggage.isFromDefault());
        assertFalse(baggage.isFromCache());

        // Verify fallback messages
        assertNotNull(baggage.getBaggageFallbackMsg());
        assertFalse(baggage.getBaggageFallbackMsg().isEmpty());

        verify(mongoClient, never()).findOne(any(), any(), any(), any());
    }

    /**
     * Input: PNR "ABC123" with baggage allowances for 2 passengers
     * ExpectedOut: Succeeded Future with Baggage containing 2 allowances (30kg and
     * 25kg checked)
     */
    @Test
    void testGetBaggageInfo_MultiplePassengers() {
        // Given
        JsonObject multiPassengerBaggage = new JsonObject()
                .put("bookingReference", "ABC123")
                .put("baggageAllowances", new JsonArray()
                        .add(new JsonObject()
                                .put("passengerNumber", 1)
                                .put("allowanceUnit", "kg")
                                .put("checkedAllowanceValue", 30)
                                .put("carryOnAllowanceValue", 10))
                        .add(new JsonObject()
                                .put("passengerNumber", 2)
                                .put("allowanceUnit", "kg")
                                .put("checkedAllowanceValue", 25)
                                .put("carryOnAllowanceValue", 7)));

        when(circuitBreaker.tryAcquirePermission()).thenReturn(true);

        doAnswer(invocation -> {
            Handler<AsyncResult<JsonObject>> handler = invocation.getArgument(3);
            AsyncResult<JsonObject> result = mock(AsyncResult.class);
            when(result.succeeded()).thenReturn(true);
            when(result.result()).thenReturn(multiPassengerBaggage);
            handler.handle(result);
            return null;
        }).when(mongoClient).findOne(any(), any(), any(), any());

        // When
        Future<Baggage> future = baggageService.getBaggageInfo("ABC123");

        // Then
        assertTrue(future.succeeded());
        Baggage baggage = future.result();
        assertEquals(2, baggage.getAllowances().size());
        assertEquals(30, baggage.getAllowances().get(0).getCheckedAllowanceValue());
        assertEquals(25, baggage.getAllowances().get(1).getCheckedAllowanceValue());
    }

    /**
     * Input: PNR "ABC123"
     * ExpectedOut: MongoDB query with single field "bookingReference":"ABC123"
     * (parameterized for NoSQL injection prevention)
     */
    @Test
    void testGetBaggageInfo_VerifyQueryFormat() {
        // Given
        when(circuitBreaker.tryAcquirePermission()).thenReturn(true);

        ArgumentCaptor<JsonObject> queryCaptor = ArgumentCaptor.forClass(JsonObject.class);

        doAnswer(invocation -> {
            Handler<AsyncResult<JsonObject>> handler = invocation.getArgument(3);
            AsyncResult<JsonObject> result = mock(AsyncResult.class);
            when(result.succeeded()).thenReturn(true);
            when(result.result()).thenReturn(validBaggageDoc);
            handler.handle(result);
            return null;
        }).when(mongoClient).findOne(eq("baggage"), queryCaptor.capture(), isNull(), any());

        // When
        baggageService.getBaggageInfo("ABC123");

        // Then - Verify MongoDB query structure (NoSQL injection prevention)
        JsonObject capturedQuery = queryCaptor.getValue();
        assertEquals("ABC123", capturedQuery.getString("bookingReference"));
        assertEquals(1, capturedQuery.size()); // Only one field, properly parameterized
    }

    /**
     * Input: PNR "TEST123", circuit breaker does not permit
     * ExpectedOut: Succeeded Future with default economy Baggage (25kg checked, 7kg
     * carry-on, no specific passenger)
     */
    @Test
    void testDefaultBaggageAllowance_Values() {
        // Given circuit breaker is open
        when(circuitBreaker.tryAcquirePermission()).thenReturn(false);

        // When
        Future<Baggage> future = baggageService.getBaggageInfo("TEST123");

        // Then - Verify standard economy defaults
        assertTrue(future.succeeded());
        Baggage baggage = future.result();
        assertEquals("TEST123", baggage.getBookingReference());
        assertTrue(baggage.isFromDefault());

        BaggageAllowance defaultAllowance = baggage.getAllowances().get(0);
        assertNull(defaultAllowance.getPassengerNumber()); // Default applies to all
        assertEquals(25, defaultAllowance.getCheckedAllowanceValue());
        assertEquals(7, defaultAllowance.getCarryOnAllowanceValue());
    }

    /**
     * Input: PNR "ABC123" with baggage in pounds (50 lbs checked, 15 lbs carry-on)
     * ExpectedOut: Succeeded Future with Baggage using "lbs" unit and correct
     * values
     */
    @Test
    void testGetBaggageInfo_DifferentUnits() {
        // Given
        JsonObject poundsBaggage = new JsonObject()
                .put("bookingReference", "ABC123")
                .put("baggageAllowances", new JsonArray()
                        .add(new JsonObject()
                                .put("passengerNumber", 1)
                                .put("allowanceUnit", "lbs")
                                .put("checkedAllowanceValue", 50)
                                .put("carryOnAllowanceValue", 15)));

        when(circuitBreaker.tryAcquirePermission()).thenReturn(true);

        doAnswer(invocation -> {
            Handler<AsyncResult<JsonObject>> handler = invocation.getArgument(3);
            AsyncResult<JsonObject> result = mock(AsyncResult.class);
            when(result.succeeded()).thenReturn(true);
            when(result.result()).thenReturn(poundsBaggage);
            handler.handle(result);
            return null;
        }).when(mongoClient).findOne(any(), any(), any(), any());

        // When
        Future<Baggage> future = baggageService.getBaggageInfo("ABC123");

        // Then
        assertTrue(future.succeeded());
        BaggageAllowance allowance = future.result().getAllowances().get(0);
        assertEquals("lbs", allowance.getAllowanceUnit());
        assertEquals(50, allowance.getCheckedAllowanceValue());
    }
}
