package com.pnr.aggregator.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TestCategory: Unit Test
 * 
 * Unit tests for custom exception classes
 * RequirementCategorized: Core Requirements (Error Handling)
 */
class ExceptionTest {

    /**
     * Input: Message "PNR not found: ABC123"
     * ExpectedOut: PNRNotFoundException created with correct message and extends
     * RuntimeException
     */
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

    /**
     * Input: Null message
     * ExpectedOut: PNRNotFoundException created with null message
     */
    @Test
    void testPNRNotFoundException_NullMessage() {
        // When
        PNRNotFoundException exception = new PNRNotFoundException(null);

        // Then
        assertNull(exception.getMessage());
    }

    /**
     * Input: Message "PNR not found: XYZ789" thrown as exception
     * ExpectedOut: PNRNotFoundException is thrown and caught
     */
    @Test
    void testPNRNotFoundException_Throwable() {
        // Given
        String message = "PNR not found: XYZ789";

        // When/Then
        assertThrows(PNRNotFoundException.class, () -> {
            throw new PNRNotFoundException(message);
        });
    }

    /**
     * Input: Message "Trip service temporarily unavailable"
     * ExpectedOut: ServiceUnavailableException created with correct message and
     * extends RuntimeException
     */
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

    /**
     * Input: Null message
     * ExpectedOut: ServiceUnavailableException created with null message
     */
    @Test
    void testServiceUnavailableException_NullMessage() {
        // When
        ServiceUnavailableException exception = new ServiceUnavailableException(null);

        // Then
        assertNull(exception.getMessage());
    }

    /**
     * Input: Message "Circuit breaker open - service unavailable" thrown as
     * exception
     * ExpectedOut: ServiceUnavailableException is thrown and caught
     */
    @Test
    void testServiceUnavailableException_Throwable() {
        // Given
        String message = "Circuit breaker open - service unavailable";

        // When/Then
        assertThrows(ServiceUnavailableException.class, () -> {
            throw new ServiceUnavailableException(message);
        });
    }

    /**
     * Input: Both exception types with test message
     * ExpectedOut: Both exceptions are RuntimeException instances but different
     * classes
     */
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

    /**
     * Input: Formatted message "PNR not found: ABC123" using String.format
     * ExpectedOut: Exception message contains PNR "ABC123" and starts with "PNR not
     * found:"
     */
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

    /**
     * Input: PNRNotFoundException with message "Test exception"
     * ExpectedOut: Exception has non-null stack trace with at least one element
     */
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
