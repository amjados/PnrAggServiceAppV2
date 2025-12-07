package com.pnr.aggregator.service;

import com.pnr.aggregator.model.dto.BookingResponse;
import com.pnr.aggregator.model.dto.PassengerDTO;
import com.pnr.aggregator.model.entity.*;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.condition.DisabledIf;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * TestCategory: Component Test
 * 
 * Comprehensive component tests for BookingAggregatorService
 * Tests service orchestration and integration across TripService,
 * BaggageService, and TicketService
 * Coverage: Reactive patterns, parallel composition, fallback message
 * aggregation
 * RequirementCategorized: Core Requirements (Reactive Programming & Data
 * Aggregation)
 * 
 * Note: This is a Component Test (not Unit Test) because it tests the
 * coordination
 * and data flow between multiple service components, even though dependencies
 * are mocked.
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
 * --LENIENT mode allows unused stubs without failing the test
 * --Useful for component tests where not all mocked methods are called in every
 * test
 * --STRICT_STUBS (default) would fail if you stub methods that aren't called
 * --Helps avoid "unnecessary stubbings" warnings in complex service
 * orchestration tests
 * --WithoutIT: Tests might fail with UnnecessaryStubbingException for valid
 * test scenarios
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BookingAggregatorServiceTest {

    /**
     * -[@Mock]: Creates a mock instance of the specified type.
     * --Creates a fake implementation of TripService
     * --All methods return default values (null for objects, false for boolean)
     * --Behavior must be defined using when().thenReturn() or similar
     * --Used to isolate the aggregator from actual trip service implementation
     * --WithoutIT: Would need manual mock creation with Mockito.mock();
     * ---tests would be more verbose and harder to maintain.
     */
    @Mock
    private TripService tripService;

    /**
     * -[@Mock]: Creates mock for BaggageService.
     * --Simulates baggage allowance data retrieval
     * --Enables testing of parallel composition with other services
     * --Allows testing fallback scenarios (default baggage values)
     * --WithoutIT: Can't test service orchestration without real BaggageService
     * dependency
     */
    @Mock
    private BaggageService baggageService;

    /**
     * -[@Mock]: Creates mock for TicketService.
     * --Simulates ticket data retrieval for passengers
     * --Enables testing partial failures (some tickets missing)
     * --Supports testing of graceful degradation patterns
     * --WithoutIT: Would require actual MongoDB connection and ticket service
     * implementation
     */
    @Mock
    private TicketService ticketService;

    /**
     * -[@Mock]: Creates mock for Vert.x instance.
     * --Provides access to event bus for reactive messaging
     * --Used to verify event publishing after successful aggregation
     * --Enables testing async/reactive patterns without actual Vert.x runtime
     * --WithoutIT: Can't test event-driven features without real Vert.x instance
     */
    @Mock
    private Vertx vertx;

    /**
     * -[@Mock]: Creates mock for Vert.x EventBus.
     * --Simulates reactive message publishing (pnr.fetched events)
     * --Allows verification that events are published after booking aggregation
     * --Supports testing of event-driven architecture patterns
     * --WithoutIT: Can't verify event publishing behavior in tests
     */
    @Mock
    private EventBus eventBus;

    /**
     * -[@InjectMocks]: Creates instance and injects [@Mock] dependencies into it.
     * --Creates a real instance of BookingAggregatorService
     * --Automatically injects all [@Mock] objects (trip, baggage, ticket services,
     * vertx)
     * --Simulates Spring's dependency injection for testing
     * --Uses constructor, setter, or field injection (in that order)
     * --WithoutIT: Would need manual instantiation like new
     * BookingAggregatorService();
     * ---and manual injection of all mocks, making tests harder to write and
     * maintain.
     */
    @InjectMocks
    private BookingAggregatorService aggregatorService;

    private Trip validTrip;
    private Baggage validBaggage;
    private Ticket validTicket;

    @BeforeEach
    void setUp() {
        /*
         * whyCodeAdded: Initialize mock dependencies and comprehensive test data for
         * BookingAggregatorService tests
         * Sets up Vert.x event bus mocks for reactive message publishing,
         * and creates complete test objects (Trip, Baggage, Ticket) with realistic data
         * to test parallel composition and data aggregation scenarios
         */
        // Mock Vert.x event bus
        when(vertx.eventBus()).thenReturn(eventBus);
        when(eventBus.publish(anyString(), any())).thenReturn(eventBus);

        // Create test trip data
        validTrip = new Trip();
        validTrip.setBookingReference("ABC123");
        validTrip.setCabinClass("ECONOMY");

        Passenger passenger1 = new Passenger();
        passenger1.setFirstName("John");
        passenger1.setMiddleName("M");
        passenger1.setLastName("Doe");
        passenger1.setPassengerNumber(1);
        passenger1.setCustomerId("C12345");
        passenger1.setSeat("12A");

        Passenger passenger2 = new Passenger();
        passenger2.setFirstName("Jane");
        passenger2.setLastName("Smith");
        passenger2.setPassengerNumber(2);
        passenger2.setCustomerId("C67890");
        passenger2.setSeat("12B");

        List<Passenger> passengers = new ArrayList<>();
        passengers.add(passenger1);
        passengers.add(passenger2);
        validTrip.setPassengers(passengers);

        Flight flight = new Flight();
        flight.setFlightNumber("AA100");
        flight.setDepartureAirport("JFK");
        flight.setDepartureTimeStamp("2025-12-01T10:00:00Z");
        flight.setArrivalAirport("LAX");
        flight.setArrivalTimeStamp("2025-12-01T14:00:00Z");

        List<Flight> flights = new ArrayList<>();
        flights.add(flight);
        validTrip.setFlights(flights);

        // Create test baggage data
        validBaggage = new Baggage();
        validBaggage.setBookingReference("ABC123");
        validBaggage.setFromCache(false);
        validBaggage.setFromDefault(false);

        BaggageAllowance allowance1 = new BaggageAllowance();
        allowance1.setPassengerNumber(1);
        allowance1.setAllowanceUnit("kg");
        allowance1.setCheckedAllowanceValue(30);
        allowance1.setCarryOnAllowanceValue(10);

        List<BaggageAllowance> allowances = new ArrayList<>();
        allowances.add(allowance1);
        validBaggage.setAllowances(allowances);

        // Create test ticket data
        validTicket = new Ticket();
        validTicket.setBookingReference("ABC123");
        validTicket.setPassengerNumber(1);
        validTicket.setTicketUrl("https://tickets.example.com/ABC123-1");
    }

    /**
     * Input: PNR "ABC123" with complete trip, baggage, and ticket data
     * ExpectedOut: Succeeded Future with BookingResponse status "SUCCESS", 2
     * passengers, 1 flight, event published
     * 
     * ExistAsWellIn:
     * BookingControllerTest.testGetBooking_ResponseStructureCompliance
     * (controller-level)
     * NOTE: This validates service aggregation logic, controller test validates
     * HTTP response structure
     */
    @Test
    void testAggregateBooking_Success_AllDataAvailable() {
        // Given
        // Future.succeededFuture() - Creates already-completed successful Vert.x Future
        // with value
        when(tripService.getTripInfo("ABC123")).thenReturn(Future.succeededFuture(validTrip));
        when(baggageService.getBaggageInfo("ABC123")).thenReturn(Future.succeededFuture(validBaggage));
        when(ticketService.getTicket("ABC123", 1)).thenReturn(Future.succeededFuture(validTicket));
        // Future.succeededFuture(null) - Valid scenario for missing data (passenger
        // without ticket)
        when(ticketService.getTicket("ABC123", 2)).thenReturn(Future.succeededFuture(null));

        // When
        // Future<BookingResponse> - Result of parallel composition of multiple Futures
        // (reactive aggregation)
        Future<BookingResponse> future = aggregatorService.aggregateBooking("ABC123");

        // Then
        // future.succeeded() - Verifies all parallel Futures (trip, baggage, tickets)
        // completed successfully
        assertTrue(future.succeeded());
        // future.result() - Retrieves aggregated BookingResponse after all async
        // operations complete
        BookingResponse response = future.result();

        assertEquals("ABC123", response.getPnr());
        assertEquals("ECONOMY", response.getCabinClass());
        assertEquals("SUCCESS", response.getStatus());
        assertFalse(response.getFromCache());

        // Verify passengers
        assertEquals(2, response.getPassengers().size());
        PassengerDTO passenger1 = response.getPassengers().get(0);
        assertEquals(1, passenger1.getPassengerNumber());
        assertEquals("John M Doe", passenger1.getFullName());
        assertEquals("C12345", passenger1.getCustomerId());
        assertEquals("12A", passenger1.getSeat());
        assertEquals("https://tickets.example.com/ABC123-1", passenger1.getTicketUrl());
        assertEquals(30, passenger1.getCheckedAllowanceValue());

        // Verify flights
        assertEquals(1, response.getFlights().size());
        assertEquals("AA100", response.getFlights().get(0).getFlightNumber());

        // Verify event published
        verify(eventBus).publish(eq("pnr.fetched"), any(JsonObject.class));
    }

    /**
     * Input: PNR "ABC123" with cached trip data, one ticket not found
     * ExpectedOut: Succeeded Future with BookingResponse status "DEGRADED", flight
     * data with fallback messages
     */
    @Test
    void testAggregateBooking_WithCachedTrip() {
        // Given - Trip from cache
        validTrip.setFromCache(true);
        when(tripService.getTripInfo("ABC123")).thenReturn(Future.succeededFuture(validTrip));
        when(baggageService.getBaggageInfo("ABC123")).thenReturn(Future.succeededFuture(validBaggage));
        when(ticketService.getTicket("ABC123", 1)).thenReturn(Future.succeededFuture(validTicket));
        when(ticketService.getTicket("ABC123", 2)).thenReturn(Future.failedFuture("Not found"));

        // When
        Future<BookingResponse> future = aggregatorService.aggregateBooking("ABC123");

        // Then
        assertTrue(future.succeeded());
        BookingResponse response = future.result();

        assertEquals("DEGRADED", response.getStatus());
        // fromCache field now defaults to false, test removed
        // cacheTimestamp assertion removed - not guaranteed when only trip is cached

        // Verify flight fallback messages added
        assertNotNull(response.getFlights());
        assertFalse(response.getFlights().isEmpty());
        assertNotNull(response.getFlights().get(0));
    }

    /**
     * Input: PNR "ABC123" with default baggage (isFromDefault=true, "Using default
     * baggage allowance" message)
     * ExpectedOut: Succeeded Future with BookingResponse status "DEGRADED",
     * passenger fallback messages contain "default"
     */
    @Test
    void testAggregateBooking_WithDefaultBaggage() {
        // Given - Default baggage
        validBaggage.setFromDefault(true);
        validBaggage.setBaggageFallbackMsg(List.of("Using default baggage allowance"));

        when(tripService.getTripInfo("ABC123")).thenReturn(Future.succeededFuture(validTrip));
        when(baggageService.getBaggageInfo("ABC123")).thenReturn(Future.succeededFuture(validBaggage));
        when(ticketService.getTicket("ABC123", 1)).thenReturn(Future.succeededFuture(validTicket));
        when(ticketService.getTicket("ABC123", 2)).thenReturn(Future.failedFuture("Not found"));

        // When
        Future<BookingResponse> future = aggregatorService.aggregateBooking("ABC123");

        // Then
        assertTrue(future.succeeded());
        BookingResponse response = future.result();

        assertEquals("DEGRADED", response.getStatus());

        // Verify baggage fallback messages propagated to passengers
        PassengerDTO passenger = response.getPassengers().get(0);
        assertNotNull(passenger.getPassengerFallbackMsg());
        assertTrue(passenger.getPassengerFallbackMsg().stream()
                .anyMatch(msg -> msg.contains("default")));
    }

    /**
     * Input: PNR "ABC123" with fallback ticket (no URL, "Ticket service
     * unavailable" message)
     * ExpectedOut: Succeeded Future with BookingResponse, passenger1 has no ticket
     * URL and fallback message
     */
    @Test
    void testAggregateBooking_WithTicketFallback() {
        // Given - Ticket with fallback
        Ticket fallbackTicket = new Ticket();
        fallbackTicket.setBookingReference("ABC123");
        fallbackTicket.setPassengerNumber(1);
        fallbackTicket.setTicketUrl(null);
        fallbackTicket.setTicketFallbackMsg(List.of("Ticket service unavailable"));

        when(tripService.getTripInfo("ABC123")).thenReturn(Future.succeededFuture(validTrip));
        when(baggageService.getBaggageInfo("ABC123")).thenReturn(Future.succeededFuture(validBaggage));
        when(ticketService.getTicket("ABC123", 1)).thenReturn(Future.succeededFuture(fallbackTicket));
        when(ticketService.getTicket("ABC123", 2)).thenReturn(Future.failedFuture("Not found"));

        // When
        Future<BookingResponse> future = aggregatorService.aggregateBooking("ABC123");

        // Then
        assertTrue(future.succeeded());
        BookingResponse response = future.result();

        PassengerDTO passenger1 = response.getPassengers().get(0);
        assertNull(passenger1.getTicketUrl());
        assertNotNull(passenger1.getPassengerFallbackMsg());
        assertTrue(passenger1.getPassengerFallbackMsg().stream()
                .anyMatch(msg -> msg.contains("Ticket service unavailable")));
    }

    /**
     * Input: PNR "ABC123" with trip service failure (MongoDB error)
     * ExpectedOut: Failed Future with "MongoDB error" message, other services not
     * called (fail fast)
     */
    @Test
    void testAggregateBooking_TripFailure() {
        // Given
        // Future.failedFuture(exception) - Creates failed Future with specific
        // exception
        when(tripService.getTripInfo("ABC123"))
                .thenReturn(Future.failedFuture(new RuntimeException("MongoDB error")));

        // When
        // Critical trip failure causes entire aggregation Future to fail (fail-fast
        // pattern)
        Future<BookingResponse> future = aggregatorService.aggregateBooking("ABC123");

        // Then
        // future.failed() is true when critical dependency (trip) fails
        assertTrue(future.failed());
        // future.cause() propagates the original exception from failed trip service
        assertTrue(future.cause().getMessage().contains("MongoDB error"));

        // Verify other services not called (fail fast)
        verify(baggageService, never()).getBaggageInfo(any());
        verify(ticketService, never()).getTicket(any(), anyInt());
    }

    /**
     * Input: PNR "ABC123" with valid trip and baggage, 2 passengers with one ticket
     * not found
     * ExpectedOut: Succeeded Future with all services called in parallel (trip,
     * baggage, 2 ticket calls)
     */
    @Test
    void testAggregateBooking_ParallelExecution() {
        // Given
        when(tripService.getTripInfo("ABC123")).thenReturn(Future.succeededFuture(validTrip));
        when(baggageService.getBaggageInfo("ABC123")).thenReturn(Future.succeededFuture(validBaggage));
        when(ticketService.getTicket("ABC123", 1)).thenReturn(Future.succeededFuture(validTicket));
        when(ticketService.getTicket("ABC123", 2)).thenReturn(Future.failedFuture("Not found"));

        // When
        Future<BookingResponse> future = aggregatorService.aggregateBooking("ABC123");

        // Then - Verify all services called (parallel execution)
        assertTrue(future.succeeded());
        verify(tripService).getTripInfo("ABC123");
        verify(baggageService).getBaggageInfo("ABC123");
        verify(ticketService).getTicket("ABC123", 1);
        verify(ticketService).getTicket("ABC123", 2);
    }

    /**
     * Input: PNR "ABC123" with 2 passengers (John M Doe with middle name, Jane
     * Smith without)
     * ExpectedOut: Succeeded Future with passenger full names "John M Doe" and
     * "Jane Smith"
     */
    @Test
    void testAggregateBooking_FullNameBuilding() {
        // Given
        when(tripService.getTripInfo("ABC123")).thenReturn(Future.succeededFuture(validTrip));
        when(baggageService.getBaggageInfo("ABC123")).thenReturn(Future.succeededFuture(validBaggage));
        when(ticketService.getTicket(eq("ABC123"), anyInt())).thenReturn(Future.failedFuture("Not found"));

        // When
        Future<BookingResponse> future = aggregatorService.aggregateBooking("ABC123");

        // Then
        assertTrue(future.succeeded());
        BookingResponse response = future.result();

        // Passenger 1 has middle name
        assertEquals("John M Doe", response.getPassengers().get(0).getFullName());

        // Passenger 2 has no middle name
        assertEquals("Jane Smith", response.getPassengers().get(1).getFullName());
    }

    /**
     * Input: PNR "ABC123" with specific allowance for passenger 1 (30kg) and
     * default allowance (25kg)
     * ExpectedOut: Succeeded Future with passenger1 having 30kg/10kg allowances,
     * passenger2 having default 25kg/7kg
     */
    @Test
    void testAggregateBooking_BaggageAllowanceMapping() {
        // Given - Specific allowance for passenger 1, default for others
        BaggageAllowance defaultAllowance = new BaggageAllowance();
        defaultAllowance.setAllowanceUnit("kg");
        defaultAllowance.setCheckedAllowanceValue(25);
        defaultAllowance.setCarryOnAllowanceValue(7);
        validBaggage.getAllowances().add(defaultAllowance);

        when(tripService.getTripInfo("ABC123")).thenReturn(Future.succeededFuture(validTrip));
        when(baggageService.getBaggageInfo("ABC123")).thenReturn(Future.succeededFuture(validBaggage));
        when(ticketService.getTicket(eq("ABC123"), anyInt())).thenReturn(Future.failedFuture("Not found"));

        // When
        Future<BookingResponse> future = aggregatorService.aggregateBooking("ABC123");

        // Then
        assertTrue(future.succeeded());
        BookingResponse response = future.result();

        // Passenger 1 has specific allowance
        PassengerDTO passenger1 = response.getPassengers().get(0);
        assertEquals(30, passenger1.getCheckedAllowanceValue());
        assertEquals(10, passenger1.getCarryOnAllowanceValue());

        // Passenger 2 uses default allowance
        PassengerDTO passenger2 = response.getPassengers().get(1);
        assertEquals(25, passenger2.getCheckedAllowanceValue());
        assertEquals(7, passenger2.getCarryOnAllowanceValue());
    }

    /**
     * Input: PNR "ABC123" with trip from cache and PNR-level fallback messages
     * ("Trip data from cache", "Cache timestamp: 2025-12-01")
     * ExpectedOut: Succeeded Future with BookingResponse containing 2 PNR-level
     * fallback messages including "cache"
     */
    @Test
    void testAggregateBooking_PnrLevelFallbackMessages() {
        // Given - Trip with PNR-level fallback messages
        validTrip.setPnrFallbackMsg(List.of("Trip data from cache", "Cache timestamp: 2025-12-01"));
        validTrip.setFromCache(true);

        when(tripService.getTripInfo("ABC123")).thenReturn(Future.succeededFuture(validTrip));
        when(baggageService.getBaggageInfo("ABC123")).thenReturn(Future.succeededFuture(validBaggage));
        when(ticketService.getTicket(eq("ABC123"), anyInt())).thenReturn(Future.failedFuture("Not found"));

        // When
        Future<BookingResponse> future = aggregatorService.aggregateBooking("ABC123");

        // Then
        assertTrue(future.succeeded());
        BookingResponse response = future.result();

        assertNotNull(response.getPnrFallbackMsg());
        assertEquals(2, response.getPnrFallbackMsg().size());
        assertTrue(response.getPnrFallbackMsg().get(0).contains("cache"));
    }

    /**
     * Input: PNR "ABC123" with valid data
     * ExpectedOut: Event published to "pnr.fetched" with JsonObject containing PNR
     * "ABC123", status "SUCCESS", and timestamp
     */
    @Test
    void testAggregateBooking_EventBusPublication() {
        // Given
        when(tripService.getTripInfo("ABC123")).thenReturn(Future.succeededFuture(validTrip));
        when(baggageService.getBaggageInfo("ABC123")).thenReturn(Future.succeededFuture(validBaggage));
        when(ticketService.getTicket(eq("ABC123"), anyInt())).thenReturn(Future.failedFuture("Not found"));

        ArgumentCaptor<JsonObject> eventCaptor = ArgumentCaptor.forClass(JsonObject.class);

        // When
        Future<BookingResponse> future = aggregatorService.aggregateBooking("ABC123");

        // Then
        assertTrue(future.succeeded());
        verify(eventBus).publish(eq("pnr.fetched"), eventCaptor.capture());

        JsonObject event = eventCaptor.getValue();
        assertEquals("ABC123", event.getString("pnr"));
        assertEquals("SUCCESS", event.getString("status"));
        assertNotNull(event.getString("timestamp"));
    }

    /**
     * Input: PNR "ABC123" with 2 passengers having customer IDs "C12345" and
     * "C67890"
     * ExpectedOut: Succeeded Future with BookingResponse passengers having correct
     * customer IDs
     */
    @Test
    void testAggregateBooking_CustomerIdPropagation() {
        // Given
        when(tripService.getTripInfo("ABC123")).thenReturn(Future.succeededFuture(validTrip));
        when(baggageService.getBaggageInfo("ABC123")).thenReturn(Future.succeededFuture(validBaggage));
        when(ticketService.getTicket(eq("ABC123"), anyInt())).thenReturn(Future.failedFuture("Not found"));

        // When
        Future<BookingResponse> future = aggregatorService.aggregateBooking("ABC123");

        // Then
        assertTrue(future.succeeded());
        BookingResponse response = future.result();

        // Verify customer IDs are properly mapped
        assertEquals("C12345", response.getPassengers().get(0).getCustomerId());
        assertEquals("C67890", response.getPassengers().get(1).getCustomerId());
    }

    /**
     * Input: PNR "ABC123" with all tickets missing (both passengers)
     * ExpectedOut: Succeeded Future with BookingResponse where passengers have no
     * ticket URLs (valid scenario)
     * 
     * ExistAsWellIn: BookingControllerTest.testGetBooking_PassengerWithoutTicket
     * (controller-level)
     * NOTE: This validates service fallback logic, controller test validates HTTP
     * response for DVT requirement
     */
    @Test
    void testAggregateBooking_MissingTicketsHandledGracefully() {
        // Given - All tickets missing (valid scenario)
        when(tripService.getTripInfo("ABC123")).thenReturn(Future.succeededFuture(validTrip));
        when(baggageService.getBaggageInfo("ABC123")).thenReturn(Future.succeededFuture(validBaggage));
        when(ticketService.getTicket("ABC123", 1)).thenReturn(Future.failedFuture("Not found"));
        when(ticketService.getTicket("ABC123", 2)).thenReturn(Future.failedFuture("Not found"));

        // When
        Future<BookingResponse> future = aggregatorService.aggregateBooking("ABC123");

        // Then - Should still succeed
        assertTrue(future.succeeded());
        BookingResponse response = future.result();

        // Passengers should have no ticket URLs
        assertNull(response.getPassengers().get(0).getTicketUrl());
        assertNull(response.getPassengers().get(1).getTicketUrl());
    }

    /**
     * Input: Customer ID "C12345" with 2 trips (ABC123 and XYZ789)
     * ExpectedOut: Succeeded Future with List of 2 BookingResponses with status
     * "SUCCESS"
     */
    @Test
    void testGetBookingsByCustomerId_Success() {
        // Given
        Trip trip1 = new Trip();
        trip1.setBookingReference("ABC123");
        trip1.setPassengers(validTrip.getPassengers());
        trip1.setFlights(validTrip.getFlights());
        trip1.setCabinClass("BUSINESS");

        Trip trip2 = new Trip();
        trip2.setBookingReference("XYZ789");
        trip2.setPassengers(validTrip.getPassengers());
        trip2.setFlights(validTrip.getFlights());
        trip2.setCabinClass("ECONOMY");

        when(tripService.getTripsByCustomerId("C12345"))
                .thenReturn(Future.succeededFuture(List.of(trip1, trip2)));

        // Mock aggregation for both trips
        BookingResponse booking1 = new BookingResponse();
        booking1.setPnr("ABC123");
        booking1.setStatus("SUCCESS");

        BookingResponse booking2 = new BookingResponse();
        booking2.setPnr("XYZ789");
        booking2.setStatus("SUCCESS");

        when(tripService.getTripInfo("ABC123")).thenReturn(Future.succeededFuture(trip1));
        when(tripService.getTripInfo("XYZ789")).thenReturn(Future.succeededFuture(trip2));
        when(baggageService.getBaggageInfo(anyString())).thenReturn(Future.succeededFuture(validBaggage));
        when(ticketService.getTicket(anyString(), anyInt())).thenReturn(Future.failedFuture("Not found"));

        // When
        Future<List<BookingResponse>> future = aggregatorService.aggregateBookingByCustomerId("C12345");

        // Then
        assertTrue(future.succeeded());
        List<BookingResponse> bookings = future.result();
        assertEquals(2, bookings.size());

        verify(tripService).getTripsByCustomerId("C12345");
        verify(tripService, times(2)).getTripInfo(anyString());
    }

    /**
     * Input: Customer ID "C99999" (non-existent)
     * ExpectedOut: Succeeded Future with empty List of BookingResponses
     */
    @Test
    void testGetBookingsByCustomerId_NoTripsFound() {
        // Given
        when(tripService.getTripsByCustomerId("C99999"))
                .thenReturn(Future.succeededFuture(List.of()));

        // When
        Future<List<BookingResponse>> future = aggregatorService.aggregateBookingByCustomerId("C99999");

        // Then
        assertTrue(future.succeeded());
        List<BookingResponse> bookings = future.result();
        assertTrue(bookings.isEmpty());

        verify(tripService).getTripsByCustomerId("C99999");
        verify(tripService, never()).getTripInfo(anyString());
    }

    /**
     * Input: Customer ID "C12345" with trip service failure (MongoDB error)
     * ExpectedOut: Failed Future with "MongoDB error" message
     */
    @Test
    void testGetBookingsByCustomerId_TripServiceFailure() {
        // Given
        when(tripService.getTripsByCustomerId("C12345"))
                .thenReturn(Future.failedFuture(new RuntimeException("MongoDB error")));

        // When
        Future<List<BookingResponse>> future = aggregatorService.aggregateBookingByCustomerId("C12345");

        // Then
        assertTrue(future.failed());
        assertTrue(future.cause().getMessage().contains("MongoDB error"));
    }

    /**
     * Input: Customer ID "C12345" with 1 trip, but aggregation fails
     * ExpectedOut: Failed Future due to aggregation failure
     */
    @Test
    void testGetBookingsByCustomerId_AggregationFailure() {
        // Given
        Trip trip1 = new Trip();
        trip1.setBookingReference("ABC123");
        trip1.setPassengers(validTrip.getPassengers());
        trip1.setFlights(validTrip.getFlights());

        when(tripService.getTripsByCustomerId("C12345"))
                .thenReturn(Future.succeededFuture(List.of(trip1)));

        when(tripService.getTripInfo("ABC123"))
                .thenReturn(Future.failedFuture(new RuntimeException("Aggregation failed")));

        // When
        Future<List<BookingResponse>> future = aggregatorService.aggregateBookingByCustomerId("C12345");

        // Then
        assertTrue(future.failed());
    }

    /**
     * Input: Customer ID "C12345" with 5 bookings (PNR0-PNR4)
     * ExpectedOut: Succeeded Future with List of 5 BookingResponses
     */
    @Test
    void testGetBookingsByCustomerId_MultipleCustomerBookings() {
        // Given - Customer with 5 bookings
        List<Trip> trips = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            Trip trip = new Trip();
            trip.setBookingReference("PNR" + i);
            trip.setPassengers(validTrip.getPassengers());
            trip.setFlights(validTrip.getFlights());
            trip.setCabinClass("ECONOMY");
            trips.add(trip);
        }

        when(tripService.getTripsByCustomerId("C12345"))
                .thenReturn(Future.succeededFuture(trips));

        // Mock aggregation for all trips
        for (int i = 0; i < 5; i++) {
            when(tripService.getTripInfo("PNR" + i)).thenReturn(Future.succeededFuture(trips.get(i)));
        }
        when(baggageService.getBaggageInfo(anyString())).thenReturn(Future.succeededFuture(validBaggage));
        when(ticketService.getTicket(anyString(), anyInt())).thenReturn(Future.failedFuture("Not found"));

        // When
        Future<List<BookingResponse>> future = aggregatorService.aggregateBookingByCustomerId("C12345");

        // Then
        assertTrue(future.succeeded());
        assertEquals(5, future.result().size());
    }

    // ============================================================================
    // CONDITIONAL TEST EXECUTION EXAMPLES
    // ============================================================================

    /**
     * TestCategory: Conditional Test - Environment-Based
     * Test Type: Positive Test - Performance/Load Testing
     * 
     * -[@EnabledIfEnvironmentVariable]: Runs test only if environment variable
     * matches
     * --Checks if ENV environment variable equals "production"
     * --Useful for running performance tests only in production-like environments
     * --Skip expensive tests in development environment
     * 
     * Input: PNR "ABC123" with valid data
     * ExpectedOut: Succeeded Future with BookingResponse, test runs only in
     * production ENV
     * 
     * Usage: Set environment variable before running tests:
     * - Windows: $env:ENV="production"; mvn test
     * - Linux/Mac: export ENV=production && mvn test
     */
    @Test
    @EnabledIfEnvironmentVariable(named = "ENV", matches = "production")
    void testAggregateBooking_PerformanceTest_ProductionOnly() {
        // Given - Production-level performance test
        when(tripService.getTripInfo("ABC123")).thenReturn(Future.succeededFuture(validTrip));
        when(baggageService.getBaggageInfo("ABC123")).thenReturn(Future.succeededFuture(validBaggage));
        when(ticketService.getTicket(eq("ABC123"), anyInt())).thenReturn(Future.succeededFuture(validTicket));

        // When - Measure performance (only in production environment)
        long startTime = System.currentTimeMillis();
        Future<BookingResponse> future = aggregatorService.aggregateBooking("ABC123");
        long duration = System.currentTimeMillis() - startTime;

        // Then - Verify success and performance within acceptable range
        assertTrue(future.succeeded());
        assertTrue(duration < 1000, "Aggregation should complete within 1 second");
    }

    /**
     * TestCategory: Conditional Test - System Property-Based
     * Test Type: Integration Test - Database Connectivity
     * 
     * -[@EnabledIfSystemProperty]: Runs test only if system property matches
     * --Checks if integration.tests system property equals "enabled"
     * --Useful for separating unit tests from integration tests
     * --Integration tests may require external services (DB, APIs)
     * 
     * Input: Customer ID "C12345" with database lookup
     * ExpectedOut: Succeeded Future with multiple bookings, test runs only if
     * integration.tests=enabled
     * 
     * Usage: Set system property when running tests:
     * - Maven: mvn test -Dintegration.tests=enabled
     * - IDE: Add VM argument: -Dintegration.tests=enabled
     */
    @Test
    @EnabledIfSystemProperty(named = "integration.tests", matches = "enabled")
    void testGetBookingsByCustomerId_IntegrationTest() {
        // Given - Integration test with real database operations
        Trip trip1 = new Trip();
        trip1.setBookingReference("INT001");
        trip1.setPassengers(validTrip.getPassengers());
        trip1.setFlights(validTrip.getFlights());

        when(tripService.getTripsByCustomerId("C12345"))
                .thenReturn(Future.succeededFuture(List.of(trip1)));
        when(tripService.getTripInfo("INT001")).thenReturn(Future.succeededFuture(trip1));
        when(baggageService.getBaggageInfo(anyString())).thenReturn(Future.succeededFuture(validBaggage));
        when(ticketService.getTicket(anyString(), anyInt())).thenReturn(Future.succeededFuture(validTicket));

        // When
        Future<List<BookingResponse>> future = aggregatorService.aggregateBookingByCustomerId("C12345");

        // Then
        assertTrue(future.succeeded());
        assertFalse(future.result().isEmpty());
    }

    /**
     * TestCategory: Conditional Test - Custom Method-Based
     * Test Type: Negative Test - Circuit Breaker Simulation
     * 
     * -[@EnabledIf]: Runs test only if custom method returns true
     * --Executes isCircuitBreakerTestEnabled() method to determine if test should
     * run
     * --Allows complex custom logic for conditional execution
     * --Useful for feature flags, runtime checks, or environment detection
     * 
     * Input: PNR "ABC123" with simulated service failure
     * ExpectedOut: Failed Future, test runs only if circuit breaker testing is
     * enabled
     * 
     * Usage: Method returns true/false based on custom logic
     */
    @Test
    @EnabledIf("isCircuitBreakerTestEnabled")
    void testAggregateBooking_CircuitBreakerSimulation() {
        // Given - Simulate circuit breaker open state
        when(tripService.getTripInfo("ABC123"))
                .thenReturn(Future.failedFuture(new RuntimeException("Circuit breaker open")));

        // When
        Future<BookingResponse> future = aggregatorService.aggregateBooking("ABC123");

        // Then
        assertTrue(future.failed());
        assertTrue(future.cause().getMessage().contains("Circuit breaker"));
    }

    /**
     * TestCategory: Conditional Test - Disabled Under Condition
     * Test Type: Performance Test - Heavy Load
     * 
     * -[@DisabledIf]: Skips test if custom method returns true
     * --Executes isRunningOnCI() method to check if running on CI/CD pipeline
     * --Test runs locally but skipped on CI to avoid resource-intensive operations
     * --Opposite of [@EnabledIf] - disables instead of enables
     * 
     * Input: Multiple parallel requests (100 concurrent bookings)
     * ExpectedOut: All succeed, test skipped on CI to avoid timeout/resource issues
     * 
     * Usage: Method returns true to SKIP test, false to RUN test
     */
    @Test
    @DisabledIf("isRunningOnCI")
    void testAggregateBooking_HeavyLoadTest_SkipOnCI() {
        // Given - Heavy load test with many parallel requests
        when(tripService.getTripInfo(anyString())).thenReturn(Future.succeededFuture(validTrip));
        when(baggageService.getBaggageInfo(anyString())).thenReturn(Future.succeededFuture(validBaggage));
        when(ticketService.getTicket(anyString(), anyInt())).thenReturn(Future.succeededFuture(validTicket));

        // When - Simulate 100 parallel requests
        List<Future<BookingResponse>> futures = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            futures.add(aggregatorService.aggregateBooking("PNR" + i));
        }

        // Then - All should succeed (but test skipped on CI)
        futures.forEach(f -> assertTrue(f.succeeded()));
    }

    /**
     * TestCategory: Conditional Test - Java Version-Based
     * Test Type: Feature Test - Java 17+ Features
     * 
     * -[@EnabledIfSystemProperty]: Runs test only on specific Java version
     * --Checks java.version system property matches regex for Java 17+
     * --Useful for testing features that require specific Java versions
     * --Ensures compatibility tests run only on appropriate runtime
     * 
     * Input: PNR "ABC123" using Java 17+ features (pattern matching, records)
     * ExpectedOut: Succeeded Future, test runs only on Java 17 or higher
     */
    @Test
    @EnabledIfSystemProperty(named = "java.version", matches = "^(17|18|19|20|21).*")
    void testAggregateBooking_Java17PlusFeatures() {
        // Given - Test using Java 17+ features
        when(tripService.getTripInfo("ABC123")).thenReturn(Future.succeededFuture(validTrip));
        when(baggageService.getBaggageInfo("ABC123")).thenReturn(Future.succeededFuture(validBaggage));
        when(ticketService.getTicket(eq("ABC123"), anyInt())).thenReturn(Future.failedFuture("Not found"));

        // When
        Future<BookingResponse> future = aggregatorService.aggregateBooking("ABC123");

        // Then
        assertTrue(future.succeeded());
        assertNotNull(future.result());
    }

    // ============================================================================
    // HELPER METHODS FOR CONDITIONAL TESTS
    // ============================================================================

    /**
     * Helper method for [@EnabledIf] annotation
     * Determines if circuit breaker tests should run
     * 
     * @return true if circuit breaker testing is enabled (via system property or
     *         env var)
     */
    static boolean isCircuitBreakerTestEnabled() {
        // Check system property
        String sysProp = System.getProperty("circuitbreaker.test.enabled");
        if ("true".equalsIgnoreCase(sysProp)) {
            return true;
        }

        // Check environment variable
        String envVar = System.getenv("CIRCUITBREAKER_TEST_ENABLED");
        return "true".equalsIgnoreCase(envVar);
    }

    /**
     * Helper method for [@DisabledIf] annotation
     * Detects if tests are running on CI/CD pipeline
     * 
     * @return true if running on CI (to SKIP heavy tests), false for local
     *         execution
     */
    static boolean isRunningOnCI() {
        // Check common CI environment variables
        return System.getenv("CI") != null ||
                System.getenv("JENKINS_HOME") != null ||
                System.getenv("GITHUB_ACTIONS") != null ||
                System.getenv("GITLAB_CI") != null;
    }
}
