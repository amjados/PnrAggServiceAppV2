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
 * Comprehensive unit tests for BaggageService
 * Coverage: Circuit breaker, fallback with defaults, MongoDB operations
 */
@ExtendWith(MockitoExtension.class)
class BaggageServiceTest {

    @Mock
    private MongoClient mongoClient;

    @Mock
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @Mock
    private CircuitBreaker circuitBreaker;

    @InjectMocks
    private BaggageService baggageService;

    private JsonObject validBaggageDoc;

    @BeforeEach
    void setUp() {
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
        Future<Baggage> future = baggageService.getBaggageInfo("ABC123");

        // Then
        assertTrue(future.succeeded());
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
