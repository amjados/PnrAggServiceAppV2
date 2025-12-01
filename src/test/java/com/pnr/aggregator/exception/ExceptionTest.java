package com.pnr.aggregator.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for custom exception classes
 */
class ExceptionTest {

    @Test
    void testPNRNotFoundException_CreationAndMessage() {
        // Given
        String message = "PNR not found: ABC123";

        // When
        PNRNotFoundException exception = new PNRNotFoundException(message);

        // Then
        assertNotNull(exception);
        assertEquals(message, exception.getMessage());
        assertInstanceOf(RuntimeException.class, exception);
    }

    @Test
    void testPNRNotFoundException_NullMessage() {
        // When
        PNRNotFoundException exception = new PNRNotFoundException(null);

        // Then
        assertNull(exception.getMessage());
    }

    @Test
    void testPNRNotFoundException_Throwable() {
        // Given
        String message = "PNR not found: XYZ789";

        // When/Then
        assertThrows(PNRNotFoundException.class, () -> {
            throw new PNRNotFoundException(message);
        });
    }

    @Test
    void testServiceUnavailableException_CreationAndMessage() {
        // Given
        String message = "Trip service temporarily unavailable";

        // When
        ServiceUnavailableException exception = new ServiceUnavailableException(message);

        // Then
        assertNotNull(exception);
        assertEquals(message, exception.getMessage());
        assertInstanceOf(RuntimeException.class, exception);
    }

    @Test
    void testServiceUnavailableException_NullMessage() {
        // When
        ServiceUnavailableException exception = new ServiceUnavailableException(null);

        // Then
        assertNull(exception.getMessage());
    }

    @Test
    void testServiceUnavailableException_Throwable() {
        // Given
        String message = "Circuit breaker open - service unavailable";

        // When/Then
        assertThrows(ServiceUnavailableException.class, () -> {
            throw new ServiceUnavailableException(message);
        });
    }

    @Test
    void testExceptionHierarchy() {
        // When
        PNRNotFoundException pnrException = new PNRNotFoundException("test");
        ServiceUnavailableException serviceException = new ServiceUnavailableException("test");

        // Then - Both should be runtime exceptions (unchecked)
        assertInstanceOf(RuntimeException.class, pnrException);
        assertInstanceOf(RuntimeException.class, serviceException);

        // They should not be instances of each other
        assertNotEquals(pnrException.getClass(), serviceException.getClass());
    }

    @Test
    void testExceptionMessageFormatting() {
        // Given
        String pnr = "ABC123";
        String message = String.format("PNR not found: %s", pnr);

        // When
        PNRNotFoundException exception = new PNRNotFoundException(message);

        // Then
        assertTrue(exception.getMessage().contains(pnr));
        assertTrue(exception.getMessage().startsWith("PNR not found:"));
    }

    @Test
    void testExceptionStackTrace() {
        // Given
        PNRNotFoundException exception = new PNRNotFoundException("Test exception");

        // When
        StackTraceElement[] stackTrace = exception.getStackTrace();

        // Then
        assertNotNull(stackTrace);
        assertTrue(stackTrace.length > 0);
    }
}
