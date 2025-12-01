package com.pnr.aggregator.controller;

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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for Customer ID endpoint
 * Coverage: Search by customer ID, multiple bookings, error handling
 */
@ExtendWith(MockitoExtension.class)
class CustomerIdEndpointTest {

    @Mock
    private BookingAggregatorService aggregatorService;

    @InjectMocks
    private BookingController bookingController;

    private List<BookingResponse> mockBookings;

    @BeforeEach
    void setUp() {
        mockBookings = new ArrayList<>();

        BookingResponse booking1 = new BookingResponse();
        booking1.setPnr("ABC123");
        booking1.setCabinClass("BUSINESS");
        booking1.setStatus("SUCCESS");

        BookingResponse booking2 = new BookingResponse();
        booking2.setPnr("XYZ789");
        booking2.setCabinClass("ECONOMY");
        booking2.setStatus("SUCCESS");

        mockBookings.add(booking1);
        mockBookings.add(booking2);
    }

    @Test
    void testGetBookingsByCustomerId_Success() throws ExecutionException, InterruptedException {
        // Given
        when(aggregatorService.getBookingsByCustomerId("C12345"))
                .thenReturn(Future.succeededFuture(mockBookings));

        // When
        CompletableFuture<ResponseEntity<?>> future = bookingController.getBookingsByCustomerId("C12345");

        // Then
        ResponseEntity<?> response = future.get();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertInstanceOf(Map.class, response.getBody());

        @SuppressWarnings("unchecked")
        Map<String, Object> responseBody = (Map<String, Object>) response.getBody();

        assertEquals("C12345", responseBody.get("customerId"));
        assertEquals(2, responseBody.get("count"));
        assertNotNull(responseBody.get("bookings"));
        assertNotNull(responseBody.get("timestamp"));

        @SuppressWarnings("unchecked")
        List<BookingResponse> bookings = (List<BookingResponse>) responseBody.get("bookings");
        assertEquals(2, bookings.size());

        verify(aggregatorService).getBookingsByCustomerId("C12345");
    }

    @Test
    void testGetBookingsByCustomerId_NoBookingsFound() throws ExecutionException, InterruptedException {
        // Given
        when(aggregatorService.getBookingsByCustomerId("C99999"))
                .thenReturn(Future.succeededFuture(List.of()));

        // When
        CompletableFuture<ResponseEntity<?>> future = bookingController.getBookingsByCustomerId("C99999");

        // Then
        ResponseEntity<?> response = future.get();

        assertEquals(HttpStatus.OK, response.getStatusCode());

        @SuppressWarnings("unchecked")
        Map<String, Object> responseBody = (Map<String, Object>) response.getBody();

        assertEquals("C99999", responseBody.get("customerId"));
        assertTrue(responseBody.get("message").toString().contains("No bookings found"));

        @SuppressWarnings("unchecked")
        List<BookingResponse> bookings = (List<BookingResponse>) responseBody.get("bookings");
        assertTrue(bookings.isEmpty());
    }

    @Test
    void testGetBookingsByCustomerId_ServiceUnavailable() throws ExecutionException, InterruptedException {
        // Given
        when(aggregatorService.getBookingsByCustomerId("C12345"))
                .thenReturn(Future.failedFuture(
                        new ServiceUnavailableException("Service temporarily unavailable")));

        // When
        CompletableFuture<ResponseEntity<?>> future = bookingController.getBookingsByCustomerId("C12345");

        // Then
        ResponseEntity<?> response = future.get();

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());

        @SuppressWarnings("unchecked")
        Map<String, Object> errorBody = (Map<String, Object>) response.getBody();

        assertEquals("Service Unavailable", errorBody.get("error"));
        assertTrue(errorBody.get("message").toString().contains("temporarily unavailable"));
        assertNotNull(errorBody.get("timestamp"));
    }

    @Test
    void testGetBookingsByCustomerId_InternalServerError() throws ExecutionException, InterruptedException {
        // Given
        when(aggregatorService.getBookingsByCustomerId("C12345"))
                .thenReturn(Future.failedFuture(new RuntimeException("Unexpected error")));

        // When
        CompletableFuture<ResponseEntity<?>> future = bookingController.getBookingsByCustomerId("C12345");

        // Then
        ResponseEntity<?> response = future.get();

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());

        @SuppressWarnings("unchecked")
        Map<String, Object> errorBody = (Map<String, Object>) response.getBody();

        assertEquals("Internal Server Error", errorBody.get("error"));
        assertNotNull(errorBody.get("timestamp"));
    }

    @Test
    void testGetBookingsByCustomerId_ValidFormats() throws ExecutionException, InterruptedException {
        // Given - Various valid customer ID formats
        String[] validIds = { "C123", "customer1", "CUST12345", "1234", "abc123" };

        for (String customerId : validIds) {
            when(aggregatorService.getBookingsByCustomerId(customerId))
                    .thenReturn(Future.succeededFuture(List.of()));

            // When
            CompletableFuture<ResponseEntity<?>> future = bookingController.getBookingsByCustomerId(customerId);

            // Then
            ResponseEntity<?> response = future.get();
            assertEquals(HttpStatus.OK, response.getStatusCode());
        }

        verify(aggregatorService, times(validIds.length)).getBookingsByCustomerId(anyString());
    }

    @Test
    void testGetBookingsByCustomerId_SingleBooking() throws ExecutionException, InterruptedException {
        // Given - Customer with only one booking
        BookingResponse singleBooking = new BookingResponse();
        singleBooking.setPnr("ABC123");
        singleBooking.setStatus("SUCCESS");

        when(aggregatorService.getBookingsByCustomerId("C12345"))
                .thenReturn(Future.succeededFuture(List.of(singleBooking)));

        // When
        CompletableFuture<ResponseEntity<?>> future = bookingController.getBookingsByCustomerId("C12345");

        // Then
        ResponseEntity<?> response = future.get();

        @SuppressWarnings("unchecked")
        Map<String, Object> responseBody = (Map<String, Object>) response.getBody();

        assertEquals(1, responseBody.get("count"));

        @SuppressWarnings("unchecked")
        List<BookingResponse> bookings = (List<BookingResponse>) responseBody.get("bookings");
        assertEquals(1, bookings.size());
        assertEquals("ABC123", bookings.get(0).getPnr());
    }

    @Test
    void testGetBookingsByCustomerId_MultipleBookings() throws ExecutionException, InterruptedException {
        // Given - Customer with many bookings
        List<BookingResponse> manyBookings = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            BookingResponse booking = new BookingResponse();
            booking.setPnr("PNR" + i);
            booking.setStatus("SUCCESS");
            manyBookings.add(booking);
        }

        when(aggregatorService.getBookingsByCustomerId("C12345"))
                .thenReturn(Future.succeededFuture(manyBookings));

        // When
        CompletableFuture<ResponseEntity<?>> future = bookingController.getBookingsByCustomerId("C12345");

        // Then
        ResponseEntity<?> response = future.get();

        @SuppressWarnings("unchecked")
        Map<String, Object> responseBody = (Map<String, Object>) response.getBody();

        assertEquals(5, responseBody.get("count"));

        @SuppressWarnings("unchecked")
        List<BookingResponse> bookings = (List<BookingResponse>) responseBody.get("bookings");
        assertEquals(5, bookings.size());
    }

    @Test
    void testGetBookingsByCustomerId_ResponseStructure() throws ExecutionException, InterruptedException {
        // Given
        when(aggregatorService.getBookingsByCustomerId("C12345"))
                .thenReturn(Future.succeededFuture(mockBookings));

        // When
        CompletableFuture<ResponseEntity<?>> future = bookingController.getBookingsByCustomerId("C12345");

        // Then
        ResponseEntity<?> response = future.get();

        @SuppressWarnings("unchecked")
        Map<String, Object> responseBody = (Map<String, Object>) response.getBody();

        // Verify all required fields present
        assertTrue(responseBody.containsKey("customerId"));
        assertTrue(responseBody.containsKey("bookings"));
        assertTrue(responseBody.containsKey("count"));
        assertTrue(responseBody.containsKey("timestamp"));

        // Verify timestamp format (ISO-8601)
        String timestamp = responseBody.get("timestamp").toString();
        assertTrue(timestamp.matches("\\d{4}-\\d{2}-\\d{2}T.*"));
    }

    @Test
    void testGetBookingsByCustomerId_DegradedBookings() throws ExecutionException, InterruptedException {
        // Given - Bookings with degraded status
        BookingResponse degradedBooking = new BookingResponse();
        degradedBooking.setPnr("ABC123");
        degradedBooking.setStatus("DEGRADED");
        degradedBooking.setFromCache(true);

        when(aggregatorService.getBookingsByCustomerId("C12345"))
                .thenReturn(Future.succeededFuture(List.of(degradedBooking)));

        // When
        CompletableFuture<ResponseEntity<?>> future = bookingController.getBookingsByCustomerId("C12345");

        // Then
        ResponseEntity<?> response = future.get();
        assertEquals(HttpStatus.OK, response.getStatusCode());

        @SuppressWarnings("unchecked")
        Map<String, Object> responseBody = (Map<String, Object>) response.getBody();

        @SuppressWarnings("unchecked")
        List<BookingResponse> bookings = (List<BookingResponse>) responseBody.get("bookings");
        assertEquals("DEGRADED", bookings.get(0).getStatus());
    }

    @Test
    void testGetBookingsByCustomerId_AlphanumericCustomerIds() {
        // Test that the pattern allows alphanumeric characters
        String[] validIds = { "ABC123", "123ABC", "Customer1", "1", "a" };

        for (String id : validIds) {
            assertTrue(id.matches("^[A-Za-z0-9]{1,20}$"),
                    "Customer ID " + id + " should match pattern");
        }

        // Invalid IDs
        String[] invalidIds = { "", "A".repeat(21), "ABC-123", "ABC_123", "ABC 123" };

        for (String id : invalidIds) {
            assertFalse(id.matches("^[A-Za-z0-9]{1,20}$"),
                    "Customer ID " + id + " should not match pattern");
        }
    }
}
