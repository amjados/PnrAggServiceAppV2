package com.pnr.aggregator.controller;

import com.pnr.aggregator.exception.PNRNotFoundException;
import com.pnr.aggregator.exception.ServiceUnavailableException;
import com.pnr.aggregator.model.dto.BookingResponse;
import com.pnr.aggregator.model.dto.PassengerDTO;
import com.pnr.aggregator.model.dto.FlightDTO;
import com.pnr.aggregator.service.BookingAggregatorService;
import io.vertx.core.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import jakarta.validation.ConstraintViolationException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Comprehensive unit tests for BookingController
 * Coverage: Input validation, error handling, async response handling
 */
/**
 * -[@ExtendWith](MockitoExtension.class): Integrates Mockito with JUnit 5.
 * --Enables Mockito annotations like [@Mock], [@InjectMocks], [@Spy]
 * --Initializes mocks before each test method automatically
 * --Validates mock usage after each test (detects unused stubs)
 * --Replaces the legacy [@RunWith](MockitoJUnitRunner.class) from JUnit 4
 * --WithoutIT: [@Mock] and [@InjectMocks] annotations wouldn't work;
 * ---mocks would be null, causing NullPointerException in tests.
 */
@ExtendWith(MockitoExtension.class)
class BookingControllerTest {

    /**
     * -[@Mock]: Creates a mock instance of the specified type.
     * --Creates a fake implementation of BookingAggregatorService
     * --All methods return default values (null for objects, false for boolean)
     * --Behavior must be defined using when().thenReturn() or similar
     * --Used to isolate the controller from actual service implementation
     * --WithoutIT: Would need manual mock creation with Mockito.mock();
     * ---tests would be more verbose and harder to maintain.
     * --TestType: Mocking for Isolation.
     */
    @Mock
    private BookingAggregatorService aggregatorService;

    /**
     * -[@InjectMocks]: Creates instance and injects [@Mock] dependencies into it.
     * --Creates a real instance of BookingController
     * --Automatically injects the [@Mock] aggregatorService into it
     * --Simulates Spring's dependency injection for testing
     * --Uses constructor, setter, or field injection (in that order)
     * --WithoutIT: Would need manual instantiation like new BookingController();
     * ---and manual injection of mocks, making tests harder to write.
     */
    @InjectMocks
    private BookingController bookingController;

    private BookingResponse validResponse;

    /**
     * -[@BeforeEach]: Runs before each test method in the class.
     * --Executes setup logic before every [@Test] method
     * --Used to initialize test data, reset mocks, or prepare test environment
     * --Ensures each test starts with a clean, consistent state
     * --Replaces [@Before] from JUnit 4
     * --WithoutIT: Would need to duplicate setup code in every test method;
     * ---tests would be harder to maintain and more error-prone.
     */
    @BeforeEach
    void setUp() {
        /*
         * whyCodeAdded: Create a reusable valid BookingResponse object for test cases
         * This eliminates duplication across tests and ensures consistent test data
         * for happy path scenarios where a valid booking response is needed
         */
        validResponse = new BookingResponse();
        validResponse.setPnr("ABC123");
        validResponse.setCabinClass("ECONOMY");
        validResponse.setStatus("SUCCESS");
    }

    /**
     * -[@Test]: Marks method as a test case.
     * --Identifies this method as a JUnit test to be executed
     * --Method must be non-static, non-private, and return void (or return type
     * compatible with test frameworks)
     * --Test runner will execute all [@Test] methods in the class
     * --Can throw exceptions; unhandled exceptions cause test failure
     * --WithoutIT: Method wouldn't be recognized as a test;
     * ---it would be ignored by the test runner and not executed.
     * 
     * TestCategory: Unit test
     * Test Type: Positive Test - Happy Path Scenario
     * Tests successful retrieval of booking with valid PNR
     * Input: PNR "ABC123"
     * ExpectedOut: HTTP 200 OK, BookingResponse with status "SUCCESS"
     * NOTE: Enhanced with comprehensive field validation (passengers, flights
     * arrays)
     */
    @Test
    void testGetBooking_Success() throws ExecutionException, InterruptedException {
        // Given - Enhanced response with passengers and flights for comprehensive
        // validation
        PassengerDTO passenger = new PassengerDTO();
        passenger.setPassengerNumber(1);
        passenger.setFullName("John Doe");
        passenger.setSeat("12A");
        passenger.setCustomerId("C123");

        FlightDTO flight = new FlightDTO();
        flight.setFlightNumber("AA100");
        flight.setDepartureAirport("JFK");
        flight.setArrivalAirport("LAX");

        validResponse.setPassengers(List.of(passenger));
        validResponse.setFlights(List.of(flight));

        when(aggregatorService.aggregateBooking("ABC123"))
                .thenReturn(Future.succeededFuture(validResponse));

        // When
        CompletableFuture<ResponseEntity<?>> future = bookingController.getBooking("ABC123");

        // Then
        assertNotNull(future);
        ResponseEntity<?> response = future.get();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertInstanceOf(BookingResponse.class, response.getBody());
        BookingResponse booking = (BookingResponse) response.getBody();

        // Comprehensive field validation
        assertEquals("ABC123", booking.getPnr());
        assertEquals("ECONOMY", booking.getCabinClass());
        assertEquals("SUCCESS", booking.getStatus());

        // Validate passengers array
        assertNotNull(booking.getPassengers());
        assertEquals(1, booking.getPassengers().size());
        PassengerDTO p = booking.getPassengers().get(0);
        assertEquals(1, p.getPassengerNumber());
        assertEquals("John Doe", p.getFullName());
        assertEquals("12A", p.getSeat());
        assertEquals("C123", p.getCustomerId());

        // Validate flights array
        assertNotNull(booking.getFlights());
        assertEquals(1, booking.getFlights().size());
        FlightDTO f = booking.getFlights().get(0);
        assertEquals("AA100", f.getFlightNumber());
        assertEquals("JFK", f.getDepartureAirport());
        assertEquals("LAX", f.getArrivalAirport());

        verify(aggregatorService).aggregateBooking("ABC123");
    }

    /**
     * TestCategory: Unit test
     * Test Type: Negative Test - Error Handling
     * Tests 404 Not Found response when PNR doesn't exist
     * Input: PNR "NOTFND"
     * ExpectedOut: HTTP 404 NOT_FOUND with error message
     */
    @Test
    void testGetBooking_NotFound() throws ExecutionException, InterruptedException {
        // Given
        when(aggregatorService.aggregateBooking("NOTFND"))
                .thenReturn(Future.failedFuture(new PNRNotFoundException("PNR not found: NOTFND")));

        // When
        CompletableFuture<ResponseEntity<?>> future = bookingController.getBooking("NOTFND");

        // Then
        ResponseEntity<?> response = future.get();

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertInstanceOf(Map.class, response.getBody());

        @SuppressWarnings("unchecked")
        Map<String, Object> errorBody = (Map<String, Object>) response.getBody();
        assertEquals("Not Found", errorBody.get("error"));
        assertTrue(errorBody.get("message").toString().contains("not found"));
        assertNotNull(errorBody.get("timestamp"));
    }

    /**
     * TestCategory: Unit test
     * Test Type: Negative Test - Circuit Breaker/Resilience
     * Tests 503 Service Unavailable when downstream service fails
     * Input: PNR "ABC123", service throws ServiceUnavailableException
     * ExpectedOut: HTTP 503 SERVICE_UNAVAILABLE with circuit breaker state
     */
    @Test
    void testGetBooking_ServiceUnavailable() throws ExecutionException, InterruptedException {
        // Given
        when(aggregatorService.aggregateBooking("ABC123"))
                .thenReturn(Future.failedFuture(
                        new ServiceUnavailableException("Trip service temporarily unavailable")));

        // When
        CompletableFuture<ResponseEntity<?>> future = bookingController.getBooking("ABC123");

        // Then
        ResponseEntity<?> response = future.get();

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertInstanceOf(Map.class, response.getBody());

        @SuppressWarnings("unchecked")
        Map<String, Object> errorBody = (Map<String, Object>) response.getBody();
        assertEquals("Service Unavailable", errorBody.get("error"));
        assertTrue(errorBody.get("message").toString().contains("temporarily unavailable"));
        assertEquals("OPEN", errorBody.get("circuitBreakerState"));
        assertNotNull(errorBody.get("timestamp"));
    }

    /**
     * TestCategory: Unit test
     * Test Type: Negative Test - Exception Handling
     * Tests 500 Internal Server Error for unexpected exceptions
     * Input: PNR "ABC123", service throws RuntimeException
     * ExpectedOut: HTTP 500 INTERNAL_SERVER_ERROR
     */
    @Test
    void testGetBooking_InternalServerError() throws ExecutionException, InterruptedException {
        // Given
        when(aggregatorService.aggregateBooking("ABC123"))
                .thenReturn(Future.failedFuture(new RuntimeException("Unexpected error")));

        // When
        CompletableFuture<ResponseEntity<?>> future = bookingController.getBooking("ABC123");

        // Then
        ResponseEntity<?> response = future.get();

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertInstanceOf(Map.class, response.getBody());

        @SuppressWarnings("unchecked")
        Map<String, Object> errorBody = (Map<String, Object>) response.getBody();
        assertEquals("Internal Server Error", errorBody.get("error"));
        assertEquals("An unexpected error occurred", errorBody.get("message"));
        assertNotNull(errorBody.get("timestamp"));
    }

    /**
     * TestCategory: Unit test
     * Test Type: Negative Test - Validation Exception Handling
     * Tests [@ExceptionHandler] for constraint violations (invalid PNR format)
     * Input: ConstraintViolationException with message "PNR validation failed"
     * ExpectedOut: HTTP 400 BAD_REQUEST with validation error details
     */
    @Test
    void testHandleValidationException() {
        // Given
        ConstraintViolationException ex = mock(ConstraintViolationException.class);
        when(ex.getMessage()).thenReturn("PNR validation failed");

        // When
        ResponseEntity<?> response = bookingController.handleValidationException(ex);

        // Then
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertInstanceOf(Map.class, response.getBody());

        @SuppressWarnings("unchecked")
        Map<String, Object> errorBody = (Map<String, Object>) response.getBody();
        assertEquals("Bad Request", errorBody.get("error"));
        assertTrue(errorBody.get("message").toString().contains("Invalid PNR format"));
        assertTrue(errorBody.get("message").toString().contains("6 alphanumeric"));
        assertNotNull(errorBody.get("timestamp"));
    }

    /**
     * TestCategory: Unit test
     * Test Type: Negative Test - Generic Exception Handling
     * Tests [@ExceptionHandler] for uncaught generic exceptions
     * Input: Exception with message "Something went wrong"
     * ExpectedOut: HTTP 500 INTERNAL_SERVER_ERROR with exception message
     */
    @Test
    void testHandleException_Generic() {
        // Given
        Exception ex = new Exception("Something went wrong");

        // When
        ResponseEntity<?> response = bookingController.handleException(ex);

        // Then
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertInstanceOf(Map.class, response.getBody());

        @SuppressWarnings("unchecked")
        Map<String, Object> errorBody = (Map<String, Object>) response.getBody();
        assertEquals("Internal Server Error", errorBody.get("error"));
        assertEquals("Something went wrong", errorBody.get("message"));
        assertNotNull(errorBody.get("timestamp"));
    }

    // Test removed: testGetBooking_AsyncBehavior - incompatible with synchronous
    // mocks

    /**
     * TestCategory: Unit test
     * Test Type: Positive Test - Concurrency/Performance
     * Tests handling of multiple simultaneous requests
     * Input: Three concurrent requests for PNRs "ABC123", "XYZ789", "DEF456"
     * ExpectedOut: All three requests return HTTP 200 OK
     */
    @Test
    void testGetBooking_MultipleSimultaneousRequests() throws ExecutionException, InterruptedException {
        // Given
        when(aggregatorService.aggregateBooking(anyString()))
                .thenReturn(Future.succeededFuture(validResponse));

        // When - Multiple simultaneous requests
        CompletableFuture<ResponseEntity<?>> future1 = bookingController.getBooking("ABC123");
        CompletableFuture<ResponseEntity<?>> future2 = bookingController.getBooking("XYZ789");
        CompletableFuture<ResponseEntity<?>> future3 = bookingController.getBooking("DEF456");

        // Then - All should complete successfully
        ResponseEntity<?> response1 = future1.get();
        ResponseEntity<?> response2 = future2.get();
        ResponseEntity<?> response3 = future3.get();

        assertEquals(HttpStatus.OK, response1.getStatusCode());
        assertEquals(HttpStatus.OK, response2.getStatusCode());
        assertEquals(HttpStatus.OK, response3.getStatusCode());

        verify(aggregatorService, times(3)).aggregateBooking(anyString());
    }

    /**
     * TestCategory: Unit test
     * Test Type: Positive Test - Degraded Mode/Fallback
     * Tests handling of degraded responses from cache
     * Input: PNR "ABC123" with cached response (status="DEGRADED", fromCache=true)
     * ExpectedOut: HTTP 200 OK with DEGRADED status and fromCache=true
     */
    @Test
    void testGetBooking_DegradedStatus() throws ExecutionException, InterruptedException {
        // Given
        BookingResponse degradedResponse = new BookingResponse();
        degradedResponse.setPnr("ABC123");
        degradedResponse.setStatus("DEGRADED");
        degradedResponse.setFromCache(true);

        when(aggregatorService.aggregateBooking("ABC123"))
                .thenReturn(Future.succeededFuture(degradedResponse));

        // When
        CompletableFuture<ResponseEntity<?>> future = bookingController.getBooking("ABC123");

        // Then
        ResponseEntity<?> response = future.get();
        assertEquals(HttpStatus.OK, response.getStatusCode());

        BookingResponse booking = (BookingResponse) response.getBody();
        assertEquals("DEGRADED", booking.getStatus());
        assertTrue(booking.getFromCache());
    }

    /**
     * TestCategory: Unit test
     * Test Type: Structural Test - Response Format Validation
     * Tests error response structure and timestamp format
     * Input: PNR "ABC123" with PNRNotFoundException
     * ExpectedOut: Error response with fields: error, message, timestamp (ISO-8601
     * format)
     */
    @Test
    void testGetBooking_ErrorResponseStructure() throws ExecutionException, InterruptedException {
        // Given
        when(aggregatorService.aggregateBooking("ABC123"))
                .thenReturn(Future.failedFuture(new PNRNotFoundException("Not found")));

        // When
        CompletableFuture<ResponseEntity<?>> future = bookingController.getBooking("ABC123");

        // Then
        ResponseEntity<?> response = future.get();

        @SuppressWarnings("unchecked")
        Map<String, Object> errorBody = (Map<String, Object>) response.getBody();

        // Verify all required error fields
        assertTrue(errorBody.containsKey("error"));
        assertTrue(errorBody.containsKey("message"));
        assertTrue(errorBody.containsKey("timestamp"));

        // Verify timestamp format (ISO-8601)
        String timestamp = errorBody.get("timestamp").toString();
        assertTrue(timestamp.matches("\\d{4}-\\d{2}-\\d{2}T.*"));
    }

    /**
     * TestCategory: Unit test
     * Test Type: Structural Test - DVT Requirement Validation
     * Tests exact response structure matches DVT assignment requirements
     * Input: PNR "GHTW42" with complete booking data (2 passengers, 1 flight)
     * ExpectedOut: HTTP 200 OK with exact JSON structure:
     * - pnr (String)
     * - cabinClass (String)
     * - passengers (Array) with: passengerNumber, fullName, seat, customerId,
     * ticketUrl
     * - flights (Array) with: flightNumber, departureAirport, etc.
     * 
     * ExistAsWellIn:
     * BookingAggregatorServiceTest.testAggregateBooking_Success_AllDataAvailable
     * (service-level)
     * NOTE: This is controller-level validation, service test validates aggregation
     * logic
     */
    @Test
    void testGetBooking_ResponseStructureCompliance() throws ExecutionException, InterruptedException {
        // Given - Complete booking response matching DVT requirements
        BookingResponse completeResponse = new BookingResponse();
        completeResponse.setPnr("GHTW42");
        completeResponse.setCabinClass("ECONOMY");
        completeResponse.setStatus("SUCCESS");

        // Passenger 1: James Morgan McGill with ticket
        PassengerDTO passenger1 = new PassengerDTO();
        passenger1.setPassengerNumber(1);
        passenger1.setFullName("James Morgan McGill");
        passenger1.setSeat("32D");
        passenger1.setTicketUrl("emirates.com?ticket=someTicketRef1");
        passenger1.setAllowanceUnit("kg");
        passenger1.setCheckedAllowanceValue(25);
        passenger1.setCarryOnAllowanceValue(7);

        // Passenger 2: Charles McGill with ticket and customer ID
        PassengerDTO passenger2 = new PassengerDTO();
        passenger2.setPassengerNumber(2);
        passenger2.setCustomerId("1216");
        passenger2.setFullName("Charles McGill");
        passenger2.setSeat("31D");
        passenger2.setTicketUrl("emirates.com?ticket=someTicketRef");
        passenger2.setAllowanceUnit("kg");
        passenger2.setCheckedAllowanceValue(25);
        passenger2.setCarryOnAllowanceValue(7);

        List<PassengerDTO> passengers = new ArrayList<>();
        passengers.add(passenger1);
        passengers.add(passenger2);
        completeResponse.setPassengers(passengers);

        // Flight: EK231 from DXB to IAD
        FlightDTO flight = new FlightDTO();
        flight.setFlightNumber("EK231");
        flight.setDepartureAirport("DXB");
        flight.setDepartureTimeStamp("2025-11-11T02:25:00+00:00");
        flight.setArrivalAirport("IAD");
        flight.setArrivalTimeStamp("2025-11-11T08:10:00+00:00");

        List<FlightDTO> flights = new ArrayList<>();
        flights.add(flight);
        completeResponse.setFlights(flights);

        when(aggregatorService.aggregateBooking("GHTW42"))
                .thenReturn(Future.succeededFuture(completeResponse));

        // When
        CompletableFuture<ResponseEntity<?>> future = bookingController.getBooking("GHTW42");

        // Then
        ResponseEntity<?> response = future.get();
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertInstanceOf(BookingResponse.class, response.getBody());

        BookingResponse booking = (BookingResponse) response.getBody();

        // Verify top-level fields
        assertEquals("GHTW42", booking.getPnr());
        assertEquals("ECONOMY", booking.getCabinClass());
        assertNotNull(booking.getPassengers());
        assertNotNull(booking.getFlights());

        // Verify passengers array structure
        assertEquals(2, booking.getPassengers().size());

        // Verify Passenger 1 fields (DVT requirement: fullName not firstName/lastName)
        PassengerDTO p1 = booking.getPassengers().get(0);
        assertEquals(1, p1.getPassengerNumber());
        assertEquals("James Morgan McGill", p1.getFullName());
        assertEquals("32D", p1.getSeat());
        assertNotNull(p1.getTicketUrl());
        assertEquals("kg", p1.getAllowanceUnit());
        assertEquals(25, p1.getCheckedAllowanceValue());
        assertEquals(7, p1.getCarryOnAllowanceValue());

        // Verify Passenger 2 fields with customer ID
        PassengerDTO p2 = booking.getPassengers().get(1);
        assertEquals(2, p2.getPassengerNumber());
        assertEquals("1216", p2.getCustomerId());
        assertEquals("Charles McGill", p2.getFullName());
        assertEquals("31D", p2.getSeat());
        assertEquals("emirates.com?ticket=someTicketRef", p2.getTicketUrl());

        // Verify flights array structure
        assertEquals(1, booking.getFlights().size());
        FlightDTO f1 = booking.getFlights().get(0);
        assertEquals("EK231", f1.getFlightNumber());
        assertEquals("DXB", f1.getDepartureAirport());
        assertEquals("2025-11-11T02:25:00+00:00", f1.getDepartureTimeStamp());
        assertEquals("IAD", f1.getArrivalAirport());
        assertEquals("2025-11-11T08:10:00+00:00", f1.getArrivalTimeStamp());

        verify(aggregatorService).aggregateBooking("GHTW42");
    }

    /**
     * TestCategory: Unit test
     * Test Type: Positive Test - DVT Requirement (Passenger Without Ticket)
     * Tests handling of passengers without eTicket as per DVT requirements
     * Input: PNR "GHTW42" with 2 passengers (passenger 1 has NO ticket, passenger 2
     * has ticket)
     * ExpectedOut: HTTP 200 OK with:
     * - Passenger 1: NO ticketUrl field (field absent, not null)
     * - Passenger 2: Has ticketUrl field
     * - Response still succeeds (valid scenario per DVT)
     * 
     * ExistAsWellIn:
     * BookingAggregatorServiceTest.testAggregateBooking_MissingTicketsHandledGracefully
     * (service-level)
     * NOTE: This is controller-level validation of DVT requirement, service test
     * validates aggregation logic
     */
    @Test
    void testGetBooking_PassengerWithoutTicket() throws ExecutionException, InterruptedException {
        // Given - Booking with one passenger WITHOUT ticket (DVT requirement)
        BookingResponse mixedResponse = new BookingResponse();
        mixedResponse.setPnr("GHTW42");
        mixedResponse.setCabinClass("ECONOMY");
        mixedResponse.setStatus("SUCCESS");

        // Passenger 1: James Morgan McGill WITHOUT ticket (DVT scenario)
        PassengerDTO passenger1 = new PassengerDTO();
        passenger1.setPassengerNumber(1);
        passenger1.setFullName("James Morgan McGill");
        passenger1.setSeat("32D");
        // NO ticketUrl set - passenger without ticket
        passenger1.setAllowanceUnit("kg");
        passenger1.setCheckedAllowanceValue(25);
        passenger1.setCarryOnAllowanceValue(7);

        // Passenger 2: Charles McGill WITH ticket
        PassengerDTO passenger2 = new PassengerDTO();
        passenger2.setPassengerNumber(2);
        passenger2.setCustomerId("1216");
        passenger2.setFullName("Charles McGill");
        passenger2.setSeat("31D");
        passenger2.setTicketUrl("emirates.com?ticket=someTicketRef");
        passenger2.setAllowanceUnit("kg");
        passenger2.setCheckedAllowanceValue(25);
        passenger2.setCarryOnAllowanceValue(7);

        List<PassengerDTO> passengers = new ArrayList<>();
        passengers.add(passenger1);
        passengers.add(passenger2);
        mixedResponse.setPassengers(passengers);

        FlightDTO flight = new FlightDTO();
        flight.setFlightNumber("EK231");
        flight.setDepartureAirport("DXB");
        flight.setDepartureTimeStamp("2025-11-11T02:25:00+00:00");
        flight.setArrivalAirport("IAD");
        flight.setArrivalTimeStamp("2025-11-11T08:10:00+00:00");

        List<FlightDTO> flights = new ArrayList<>();
        flights.add(flight);
        mixedResponse.setFlights(flights);

        when(aggregatorService.aggregateBooking("GHTW42"))
                .thenReturn(Future.succeededFuture(mixedResponse));

        // When
        CompletableFuture<ResponseEntity<?>> future = bookingController.getBooking("GHTW42");

        // Then
        ResponseEntity<?> response = future.get();
        assertEquals(HttpStatus.OK, response.getStatusCode());

        BookingResponse booking = (BookingResponse) response.getBody();
        assertNotNull(booking);
        assertEquals(2, booking.getPassengers().size());

        // Verify Passenger 1 has NO ticketUrl (DVT requirement: passenger without
        // ticket)
        PassengerDTO p1 = booking.getPassengers().get(0);
        assertEquals(1, p1.getPassengerNumber());
        assertEquals("James Morgan McGill", p1.getFullName());
        assertEquals("32D", p1.getSeat());
        assertNull(p1.getTicketUrl(), "Passenger without ticket should have null ticketUrl");

        // Verify Passenger 2 HAS ticketUrl
        PassengerDTO p2 = booking.getPassengers().get(1);
        assertEquals(2, p2.getPassengerNumber());
        assertEquals("1216", p2.getCustomerId());
        assertEquals("Charles McGill", p2.getFullName());
        assertEquals("31D", p2.getSeat());
        assertNotNull(p2.getTicketUrl(), "Passenger with ticket should have ticketUrl");
        assertEquals("emirates.com?ticket=someTicketRef", p2.getTicketUrl());

        verify(aggregatorService).aggregateBooking("GHTW42");
    }

}
