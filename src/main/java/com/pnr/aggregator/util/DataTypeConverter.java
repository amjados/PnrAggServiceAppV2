package com.pnr.aggregator.util;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * Utility class for data type conversions, specifically for handling timestamp
 * conversions
 * in an asynchronous (non-blocking) manner using Vert.x.
 * 
 * This class provides static methods to convert various timestamp formats to
 * LocalDateTime
 * using Vert.x's event loop for non-blocking operations.
 */
public class DataTypeConverter {

    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_DATE_TIME;
    private static final DateTimeFormatter ISO_INSTANT_FORMATTER = DateTimeFormatter.ISO_INSTANT;

    /**
     * Converts a timestamp string to LocalDateTime asynchronously using Vert.x.
     * 
     * Supports multiple timestamp formats:
     * - ISO 8601 date-time format (e.g., "2024-12-15T10:30:00")
     * - ISO 8601 instant format (e.g., "2024-12-15T10:30:00Z")
     * - Unix epoch milliseconds (e.g., "1702641000000")
     * 
     * The conversion is executed on the Vert.x event loop to maintain non-blocking
     * behavior.
     * 
     * @param vertx     The Vert.x instance for executing async operations
     * @param timestamp The timestamp string to convert
     * @return Future<LocalDateTime> containing the converted date-time or failure
     * 
     * @deprecated This method uses vertx.executeBlocking which is deprecated in
     *             newer Vert.x versions.
     *             Consider using vertx.executeBlocking(Callable, boolean) or the
     *             new Worker API.
     */
    @Deprecated
    public static Future<LocalDateTime> timestampsToDateLocal(Vertx vertx, String timestamp) {
        Promise<LocalDateTime> promise = Promise.promise();

        if (timestamp == null || timestamp.isEmpty()) {
            promise.fail(new IllegalArgumentException("Timestamp cannot be null or empty"));
            return promise.future();
        }

        // Execute blocking code on worker thread pool
        vertx.executeBlocking(blockingPromise -> {
            try {
                LocalDateTime dateTime = parseTimestamp(timestamp);
                blockingPromise.complete(dateTime);
            } catch (Exception e) {
                blockingPromise.fail(e);
            }
        }, result -> {
            if (result.succeeded()) {
                promise.complete((LocalDateTime) result.result());
            } else {
                promise.fail(result.cause());
            }
        });

        return promise.future();
    }

    /**
     * Parse timestamp string into LocalDateTime.
     * Attempts multiple parsing strategies in order.
     * 
     * @param timestamp The timestamp string to parse
     * @return LocalDateTime parsed from the timestamp
     * @throws DateTimeParseException if timestamp cannot be parsed
     */
    private static LocalDateTime parseTimestamp(String timestamp) throws DateTimeParseException {
        // Try ISO 8601 date-time format first (e.g., "2024-12-15T10:30:00")
        try {
            return LocalDateTime.parse(timestamp, ISO_FORMATTER);
        } catch (DateTimeParseException e1) {
            // Try ISO instant format (e.g., "2024-12-15T10:30:00Z")
            try {
                Instant instant = Instant.from(ISO_INSTANT_FORMATTER.parse(timestamp));
                return LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
            } catch (DateTimeParseException e2) {
                // Try parsing as Unix epoch milliseconds
                try {
                    long epochMilli = Long.parseLong(timestamp);
                    Instant instant = Instant.ofEpochMilli(epochMilli);
                    return LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
                } catch (NumberFormatException e3) {
                    throw new DateTimeParseException(
                            "Unable to parse timestamp: " + timestamp +
                                    ". Tried ISO date-time, ISO instant, and epoch milliseconds formats.",
                            timestamp, 0);
                }
            }
        }
    }

    /**
     * Synchronous version of timestamp conversion (for testing or non-async
     * contexts).
     * 
     * @param timestamp The timestamp string to convert
     * @return LocalDateTime parsed from the timestamp
     * @throws DateTimeParseException if timestamp cannot be parsed
     */
    public static LocalDateTime timestampsToDateLocalSync(String timestamp) throws DateTimeParseException {
        if (timestamp == null || timestamp.isEmpty()) {
            throw new IllegalArgumentException("Timestamp cannot be null or empty");
        }
        return parseTimestamp(timestamp);
    }
}
