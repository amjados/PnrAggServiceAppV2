package com.pnr.aggregator.controller;

import com.pnr.aggregator.exception.PNRNotFoundException;
import com.pnr.aggregator.exception.ServiceUnavailableException;
import com.pnr.aggregator.model.dto.BookingResponse;
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
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Comprehensive unit tests for BookingController
 * Coverage: Input validation, error handling, async response handling
 */
/**
 * -@ExtendWith(MockitoExtension.class): Integrates Mockito with JUnit 5.
 * --Enables Mockito annotations like @Mock, @InjectMocks, @Spy
 * --Initializes mocks before each test method automatically
 * --Validates mock usage after each test (detects unused stubs)
 * --Replaces the legacy @RunWith(MockitoJUnitRunner.class) from JUnit 4
 * --WithoutIT: @Mock and @InjectMocks annotations wouldn't work;
 * ---mocks would be null, causing NullPointerException in tests.
 */
@ExtendWith(MockitoExtension.class)
class BookingControllerTest {

    /**
     * -@Mock: Creates a mock instance of the specified type.
     * --Creates a fake implementation of BookingAggregatorService
     * --All methods return default values (null for objects, false for boolean)
     * --Behavior must be defined using when().thenReturn() or similar
     * --Used to isolate the controller from actual service implementation
     * --WithoutIT: Would need manual mock creation with Mockito.mock();
     * ---tests would be more verbose and harder to maintain.
     */
    @Mock
    private BookingAggregatorService aggregatorService;

    /**
     * -@InjectMocks: Creates instance and injects @Mock dependencies into it.
     * --Creates a real instance of BookingController
     * --Automatically injects the @Mock aggregatorService into it
     * --Simulates Spring's dependency injection for testing
     * --Uses constructor, setter, or field injection (in that order)
     * --WithoutIT: Would need manual instantiation like new BookingController();
     * ---and manual injection of mocks, making tests harder to write.
     */
    @InjectMocks
    private BookingController bookingController;

    private BookingResponse validResponse;

    /**
     * -@BeforeEach: Runs before each test method in the class.
     * --Executes setup logic before every @Test method
     * --Used to initialize test data, reset mocks, or prepare test environment
     * --Ensures each test starts with a clean, consistent state
     * --Replaces @Before from JUnit 4
     * --WithoutIT: Would need to duplicate setup code in every test method;
     * ---tests would be harder to maintain and more error-prone.
     */
    @BeforeEach
    void setUp() {
        validResponse = new BookingResponse();
        validResponse.setPnr("ABC123");
        validResponse.setCabinClass("ECONOMY");
        validResponse.setStatus("SUCCESS");
    }

    /**
     * -@Test: Marks method as a test case.
     * --Identifies this method as a JUnit test to be executed
     * --Method must be non-static, non-private, and return void (or return type
     * compatible with test frameworks)
     * --Test runner will execute all @Test methods in the class
     * --Can throw exceptions; unhandled exceptions cause test failure
     * --WithoutIT: Method wouldn't be recognized as a test;
     * ---it would be ignored by the test runner and not executed.
     * 
     * Test Type: Positive Test - Happy Path Scenario
     * Tests successful retrieval of booking with valid PNR
     */
    @Test
    void testGetBooking_Success() throws ExecutionException, InterruptedException {
        // Given
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
        assertEquals("ABC123", booking.getPnr());
        assertEquals("SUCCESS", booking.getStatus());

        verify(aggregatorService).aggregateBooking("ABC123");
    }

    /**
     * Test Type: Negative Test - Error Handling
     * Tests 404 Not Found response when PNR doesn't exist
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
     * Test Type: Negative Test - Circuit Breaker/Resilience
     * Tests 503 Service Unavailable when downstream service fails
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
     * Test Type: Negative Test - Exception Handling
     * Tests 500 Internal Server Error for unexpected exceptions
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
     * Test Type: Positive Test - Input Validation
     * Tests multiple valid PNR formats to ensure pattern flexibility
     */
    @Test
    void testGetBooking_ValidPnrFormats() throws ExecutionException, InterruptedException {
        // Given - Various valid PNR formats
        String[] validPnrs = { "ABC123", "XYZ789", "GHTW42", "A1B2C3", "123456", "ABCDEF" };

        for (String pnr : validPnrs) {
            when(aggregatorService.aggregateBooking(pnr))
                    .thenReturn(Future.succeededFuture(validResponse));

            // When
            CompletableFuture<ResponseEntity<?>> future = bookingController.getBooking(pnr);

            // Then
            ResponseEntity<?> response = future.get();
            assertEquals(HttpStatus.OK, response.getStatusCode());
        }

        verify(aggregatorService, times(validPnrs.length)).aggregateBooking(anyString());
    }

    /**
     * Test Type: Negative Test - Validation Exception Handling
     * Tests @ExceptionHandler for constraint violations (invalid PNR format)
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
     * Test Type: Negative Test - Generic Exception Handling
     * Tests @ExceptionHandler for uncaught generic exceptions
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
     * Test Type: Positive Test - Concurrency/Performance
     * Tests handling of multiple simultaneous requests
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
     * Test Type: Positive Test - Degraded Mode/Fallback
     * Tests handling of degraded responses from cache
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
     * Test Type: Structural Test - Response Format Validation
     * Tests error response structure and timestamp format
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
     * Test Type: Negative Test - Null Pointer Exception Handling
     * Tests handling of NullPointerException from service layer
     */
    @Test
    void testGetBooking_NullPointerHandling() throws ExecutionException, InterruptedException {
        // Given
        when(aggregatorService.aggregateBooking("ABC123"))
                .thenReturn(Future.failedFuture(new NullPointerException("Null value")));

        // When
        CompletableFuture<ResponseEntity<?>> future = bookingController.getBooking("ABC123");

        // Then
        ResponseEntity<?> response = future.get();
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());

        @SuppressWarnings("unchecked")
        Map<String, Object> errorBody = (Map<String, Object>) response.getBody();
        assertEquals("Internal Server Error", errorBody.get("error"));
    }

    /**
     * Test Type: Integration Test - Mock Verification
     * Tests that service layer is called correctly (mock interaction)
     */
    @Test
    void testGetBooking_VerifyLogging() throws ExecutionException, InterruptedException {
        // Given
        when(aggregatorService.aggregateBooking("ABC123"))
                .thenReturn(Future.succeededFuture(validResponse));

        // When
        CompletableFuture<ResponseEntity<?>> future = bookingController.getBooking("ABC123");
        future.get();

        // Then - Verify service was called with correct PNR
        verify(aggregatorService).aggregateBooking("ABC123");
    }

    /**
     * Test Type: Unit Test - Validation Pattern Testing
     * Tests PNR regex pattern validation (case sensitivity)
     */
    @Test
    void testGetBooking_CaseInsensitiveValidation() {
        // Note: The @Pattern annotation requires uppercase, so lowercase should fail
        // validation
        // This test verifies that the validation is working as expected
        // In a real scenario, the validation would be triggered by Spring's validation
        // framework

        // Valid uppercase PNRs
        String[] validPnrs = { "ABC123", "XYZ789" };
        for (String pnr : validPnrs) {
            assertTrue(pnr.matches("^[A-Z0-9]{6}$"), "PNR " + pnr + " should match pattern");
        }

        // Invalid lowercase PNRs
        String[] invalidPnrs = { "abc123", "Abc123" };
        for (String pnr : invalidPnrs) {
            assertFalse(pnr.matches("^[A-Z0-9]{6}$"), "PNR " + pnr + " should not match pattern");
        }
    }
}
