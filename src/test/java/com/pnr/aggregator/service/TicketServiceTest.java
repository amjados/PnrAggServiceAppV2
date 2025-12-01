package com.pnr.aggregator.service;

import com.pnr.aggregator.model.entity.Ticket;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
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
 * Comprehensive unit tests for TicketService
 * Coverage: Circuit breaker, missing tickets, MongoDB operations
 */
@ExtendWith(MockitoExtension.class)
class TicketServiceTest {

    @Mock
    private MongoClient mongoClient;

    @Mock
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @Mock
    private CircuitBreaker circuitBreaker;

    @InjectMocks
    private TicketService ticketService;

    private JsonObject validTicketDoc;

    @BeforeEach
    void setUp() {
        /* whyCodeAdded: Initialize circuit breaker mocks and test data for TicketService tests
         Sets up circuit breaker in CLOSED state to enable testing normal operations,
         and creates valid ticket document with URL to test MongoDB retrieval and missing ticket scenarios
        */
        // Mock circuit breaker registry
        when(circuitBreakerRegistry.circuitBreaker("ticketServiceCB")).thenReturn(circuitBreaker);
        when(circuitBreaker.getName()).thenReturn("ticketServiceCB");
        when(circuitBreaker.getState()).thenReturn(CircuitBreaker.State.CLOSED);

        // Initialize the service
        ticketService.init();

        // Create valid test data
        validTicketDoc = new JsonObject()
                .put("bookingReference", "ABC123")
                .put("passengerNumber", 1)
                .put("ticketUrl", "https://tickets.example.com/ABC123-1");
    }

    /**
     * Input: PNR "ABC123", passenger number 1
     * ExpectedOut: Succeeded Future with Ticket containing booking reference,
     * passenger number, ticket URL, and no fallback message
     */
    @Test
    void testGetTicket_Success() {
        // Given
        when(circuitBreaker.tryAcquirePermission()).thenReturn(true);

        // Mock MongoDB success
        doAnswer(invocation -> {
            Handler<AsyncResult<JsonObject>> handler = invocation.getArgument(3);
            AsyncResult<JsonObject> result = mock(AsyncResult.class);
            when(result.succeeded()).thenReturn(true);
            when(result.result()).thenReturn(validTicketDoc);
            handler.handle(result);
            return null;
        }).when(mongoClient).findOne(eq("tickets"), any(JsonObject.class), isNull(), any());

        // When
        Future<Ticket> future = ticketService.getTicket("ABC123", 1);

        // Then
        assertTrue(future.succeeded());
        Ticket ticket = future.result();
        assertNotNull(ticket);
        assertEquals("ABC123", ticket.getBookingReference());
        assertEquals(1, ticket.getPassengerNumber());
        assertEquals("https://tickets.example.com/ABC123-1", ticket.getTicketUrl());
        assertNull(ticket.getTicketFallbackMsg());

        verify(circuitBreaker).onSuccess(anyLong(), eq(TimeUnit.NANOSECONDS));
    }

    /**
     * Input: PNR "ABC123", passenger number 1, ticket not found in MongoDB
     * ExpectedOut: Failed Future with "not found" message; circuit breaker counts
     * as success (valid scenario)
     */
    @Test
    void testGetTicket_NotFound_StillSuccess() {
        // Given - Missing ticket is valid scenario
        when(circuitBreaker.tryAcquirePermission()).thenReturn(true);

        // Mock MongoDB returns null
        doAnswer(invocation -> {
            Handler<AsyncResult<JsonObject>> handler = invocation.getArgument(3);
            AsyncResult<JsonObject> result = mock(AsyncResult.class);
            when(result.succeeded()).thenReturn(true);
            when(result.result()).thenReturn(null);
            handler.handle(result);
            return null;
        }).when(mongoClient).findOne(eq("tickets"), any(JsonObject.class), isNull(), any());

        // When
        Future<Ticket> future = ticketService.getTicket("ABC123", 1);

        // Then - Missing ticket is not a circuit breaker failure
        assertTrue(future.failed());
        assertTrue(future.cause().getMessage().contains("not found"));

        // Circuit breaker counts this as success (valid business scenario)
        verify(circuitBreaker).onSuccess(anyLong(), eq(TimeUnit.NANOSECONDS));
    }

    /**
     * Input: PNR "ABC123", passenger number 1, MongoDB connection failure
     * ExpectedOut: Succeeded Future with fallback Ticket (no URL) containing
     * "unavailable" message
     */
    @Test
    void testGetTicket_MongoDbError_ReturnsFallback() {
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
        }).when(mongoClient).findOne(eq("tickets"), any(JsonObject.class), isNull(), any());

        // When
        Future<Ticket> future = ticketService.getTicket("ABC123", 1);

        // Then
        assertTrue(future.succeeded());
        Ticket ticket = future.result();
        assertNotNull(ticket);
        assertEquals("ABC123", ticket.getBookingReference());
        assertEquals(1, ticket.getPassengerNumber());
        assertNull(ticket.getTicketUrl()); // Fallback has no URL

        // Verify fallback messages
        assertNotNull(ticket.getTicketFallbackMsg());
        assertFalse(ticket.getTicketFallbackMsg().isEmpty());
        assertTrue(ticket.getTicketFallbackMsg().get(0).contains("unavailable"));

        verify(circuitBreaker).onError(anyLong(), eq(TimeUnit.NANOSECONDS), any());
    }

    /**
     * Input: PNR "ABC123", passenger number 1, circuit breaker OPEN state
     * ExpectedOut: Succeeded Future with fallback Ticket (no URL) containing
     * "unavailable" message, MongoDB not called
     */
    @Test
    void testGetTicket_CircuitBreakerOpen_ReturnsFallback() {
        // Given
        when(circuitBreaker.tryAcquirePermission()).thenReturn(false);
        when(circuitBreaker.getState()).thenReturn(CircuitBreaker.State.OPEN);

        // When
        Future<Ticket> future = ticketService.getTicket("ABC123", 1);

        // Then
        assertTrue(future.succeeded());
        Ticket ticket = future.result();
        assertNotNull(ticket);
        assertEquals("ABC123", ticket.getBookingReference());
        assertEquals(1, ticket.getPassengerNumber());
        assertNull(ticket.getTicketUrl());

        // Verify fallback messages
        assertNotNull(ticket.getTicketFallbackMsg());
        assertTrue(ticket.getTicketFallbackMsg().stream()
                .anyMatch(msg -> msg.contains("unavailable")));

        verify(mongoClient, never()).findOne(any(), any(), any(), any());
    }

    /**
     * Input: PNR "ABC123", passenger number 1
     * ExpectedOut: MongoDB query with two fields: "bookingReference":"ABC123" and
     * "passengerNumber":1 (both parameterized)
     */
    @Test
    void testGetTicket_VerifyQueryFormat() {
        // Given
        when(circuitBreaker.tryAcquirePermission()).thenReturn(true);

        ArgumentCaptor<JsonObject> queryCaptor = ArgumentCaptor.forClass(JsonObject.class);

        doAnswer(invocation -> {
            Handler<AsyncResult<JsonObject>> handler = invocation.getArgument(3);
            AsyncResult<JsonObject> result = mock(AsyncResult.class);
            when(result.succeeded()).thenReturn(true);
            when(result.result()).thenReturn(validTicketDoc);
            handler.handle(result);
            return null;
        }).when(mongoClient).findOne(eq("tickets"), queryCaptor.capture(), isNull(), any());

        // When
        ticketService.getTicket("ABC123", 1);

        // Then - Verify MongoDB query structure (NoSQL injection prevention)
        JsonObject capturedQuery = queryCaptor.getValue();
        assertEquals("ABC123", capturedQuery.getString("bookingReference"));
        assertEquals(1, capturedQuery.getInteger("passengerNumber"));
        assertEquals(2, capturedQuery.size()); // Two fields, both properly parameterized
    }

    /**
     * Input: PNR "ABC123" with passenger numbers 1 and 2
     * ExpectedOut: Two succeeded Futures with Tickets having respective passenger
     * numbers and ticket URLs ending in "-1" and "-2"
     */
    @Test
    void testGetTicket_DifferentPassengerNumbers() {
        // Given
        when(circuitBreaker.tryAcquirePermission()).thenReturn(true);

        // Mock different tickets for different passengers
        doAnswer(invocation -> {
            Handler<AsyncResult<JsonObject>> handler = invocation.getArgument(3);
            AsyncResult<JsonObject> result = mock(AsyncResult.class);
            when(result.succeeded()).thenReturn(true);

            JsonObject query = invocation.getArgument(1);
            int passengerNum = query.getInteger("passengerNumber");

            JsonObject ticketDoc = new JsonObject()
                    .put("bookingReference", "ABC123")
                    .put("passengerNumber", passengerNum)
                    .put("ticketUrl", "https://tickets.example.com/ABC123-" + passengerNum);

            when(result.result()).thenReturn(ticketDoc);
            handler.handle(result);
            return null;
        }).when(mongoClient).findOne(eq("tickets"), any(JsonObject.class), isNull(), any());

        // When
        Future<Ticket> future1 = ticketService.getTicket("ABC123", 1);
        Future<Ticket> future2 = ticketService.getTicket("ABC123", 2);

        // Then
        assertTrue(future1.succeeded());
        assertTrue(future2.succeeded());
        assertEquals(1, future1.result().getPassengerNumber());
        assertEquals(2, future2.result().getPassengerNumber());
        assertTrue(future1.result().getTicketUrl().endsWith("-1"));
        assertTrue(future2.result().getTicketUrl().endsWith("-2"));
    }

    /**
     * Input: PNR "TEST123", passenger number 5, circuit breaker does not permit
     * ExpectedOut: Succeeded Future with fallback Ticket having no URL and 2
     * fallback messages
     */
    @Test
    void testGetTicket_FallbackTicketStructure() {
        // Given
        when(circuitBreaker.tryAcquirePermission()).thenReturn(false);

        // When
        Future<Ticket> future = ticketService.getTicket("TEST123", 5);

        // Then - Verify fallback ticket structure
        assertTrue(future.succeeded());
        Ticket ticket = future.result();
        assertEquals("TEST123", ticket.getBookingReference());
        assertEquals(5, ticket.getPassengerNumber());
        assertNull(ticket.getTicketUrl());
        assertNotNull(ticket.getTicketFallbackMsg());
        assertEquals(2, ticket.getTicketFallbackMsg().size());
    }

    /**
     * Input: PNR "ABC123" with 3 calls (passenger numbers 1, 2, 3), all MongoDB
     * connection timeouts
     * ExpectedOut: Three succeeded Futures with fallback Tickets; circuit breaker
     * records 3 errors
     */
    @Test
    void testGetTicket_MultipleMongoErrors() {
        // Given - Test that circuit breaker tracks multiple errors
        when(circuitBreaker.tryAcquirePermission()).thenReturn(true);

        doAnswer(invocation -> {
            Handler<AsyncResult<JsonObject>> handler = invocation.getArgument(3);
            AsyncResult<JsonObject> result = mock(AsyncResult.class);
            when(result.succeeded()).thenReturn(false);
            when(result.cause()).thenReturn(new RuntimeException("Connection timeout"));
            handler.handle(result);
            return null;
        }).when(mongoClient).findOne(eq("tickets"), any(JsonObject.class), isNull(), any());

        // When - Multiple calls
        Future<Ticket> future1 = ticketService.getTicket("ABC123", 1);
        Future<Ticket> future2 = ticketService.getTicket("ABC123", 2);
        Future<Ticket> future3 = ticketService.getTicket("ABC123", 3);

        // Then - All should get fallback
        assertTrue(future1.succeeded());
        assertTrue(future2.succeeded());
        assertTrue(future3.succeeded());

        // Verify circuit breaker recorded all errors
        verify(circuitBreaker, times(3)).onError(anyLong(), eq(TimeUnit.NANOSECONDS), any());
    }

    /**
     * Input: PNR "ABC123", passenger number 1
     * ExpectedOut: MongoDB query with passengerNumber as Integer type (type-safe,
     * prevents injection)
     */
    @Test
    void testGetTicket_ParameterizedQuery_TypeSafety() {
        // Given
        when(circuitBreaker.tryAcquirePermission()).thenReturn(true);

        ArgumentCaptor<JsonObject> queryCaptor = ArgumentCaptor.forClass(JsonObject.class);

        doAnswer(invocation -> {
            Handler<AsyncResult<JsonObject>> handler = invocation.getArgument(3);
            AsyncResult<JsonObject> result = mock(AsyncResult.class);
            when(result.succeeded()).thenReturn(true);
            when(result.result()).thenReturn(validTicketDoc);
            handler.handle(result);
            return null;
        }).when(mongoClient).findOne(eq("tickets"), queryCaptor.capture(), isNull(), any());

        // When
        ticketService.getTicket("ABC123", 1);

        // Then - Verify type safety (int is primitive, preventing injection)
        JsonObject query = queryCaptor.getValue();
        Object passengerNumber = query.getValue("passengerNumber");
        assertInstanceOf(Integer.class, passengerNumber);
    }
}
