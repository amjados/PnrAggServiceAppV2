package com.pnr.aggregator.controller;

import com.pnr.aggregator.exception.PNRNotFoundException;
import com.pnr.aggregator.exception.ServiceUnavailableException;
import com.pnr.aggregator.service.BookingAggregatorService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.constraints.Pattern;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * -@RestController: Combines [@Controller] and [@ResponseBody]
 * --Marks this class as a Spring MVC controller that handles HTTP requests
 * --Automatically serializes return values to JSON (no need for [@ResponseBody]
 * on each method)
 * --WithoutIT: This class won't handle HTTP requests;
 * all API endpoints would return 404 Not Found.
 * =========
 * -@RequestMapping("/booking"): Maps HTTP requests to handler methods
 * --All methods in this controller will handle requests starting with /booking
 * --Example: /booking/ABC123 will be handled by methods with additional path
 * mappings
 * --WithoutIT: Endpoints wouldn't have the /booking prefix;
 * URLs would need to change, breaking API compatibility.
 * =========
 * -@Validated: Enables method parameter validation using Jakarta Validation
 * (JSR-380)
 * --Validates method parameters annotated with [@Pattern], [@NotNull], [@Size],
 * etc.
 * --If validation fails, throws ConstraintViolationException (handled
 * by [@ExceptionHandler])
 * --WithoutIT: [@Pattern] validation on parameters wouldn't work;
 * invalid PNR formats could reach the service layer, risking injection attacks.
 * =========
 * -@Slf4j: Lombok annotation that generates a SLF4J Logger field
 * --Creates a private static final Logger log =
 * LoggerFactory.getLogger(BookingController.class)
 * --Allows using log.info(), log.error(), log.debug() without manually creating
 * logger
 */
@RestController
@RequestMapping("/booking")
@Validated
@Slf4j
public class BookingController {

    /**
     * -@Autowired: Enables dependency injection
     * --Spring automatically injects an instance of BookingAggregatorService
     * --Finds the bean by type from the application context
     * --No need for manual instantiation or constructor injection
     * --WithoutIT: aggregatorService would be null;
     * all API calls would fail with NullPointerException.
     */
    @Autowired
    private BookingAggregatorService aggregatorService;

    /**
     * Get booking by PNR
     * 
     * -@param pnr Passenger Name Record (6 alphanumeric characters)
     * Format: ^[A-Z0-9]{6}$ (uppercase letters and digits only)
     * Examples: GHTW42, ABC123
     * -@return Booking details with trip, baggage, and ticket information
     * 
     * Input Validation:
     * - PNR must be exactly 6 characters
     * - Only uppercase letters (A-Z) and digits (0-9) allowed
     * - Prevents injection attacks by restricting character set
     * - Sanitizes input before database queries
     */
    /**
     * -@GetMapping("/{pnr}"): Maps HTTP GET requests to this method
     * --Handles GET requests to /booking/{pnr} (e.g., /booking/ABC123)
     * --{pnr} is a path variable that will be extracted from the URL
     * --Shorthand for [@RequestMapping](value = "/{pnr}", method =
     * RequestMethod.GET)
     * --WithoutIT: This method wouldn't handle GET requests;
     * API endpoint /booking/{pnr} would return 404.
     */
    @GetMapping("/{pnr}")
    public CompletableFuture<ResponseEntity<?>> getBooking(
            /**
             * -@PathVariable: Extracts value from URI path
             * --Binds the {pnr} placeholder in the URL to the method
             * parameter
             * --Example: /booking/ABC123 -> pnr = "ABC123"
             * --WithoutIT: pnr parameter would be null;
             * method couldn't access the PNR from the URL.
             * =========
             * -@Pattern: Jakarta Validation constraint for regex matching
             * --Validates that pnr matches ^[A-Z0-9]{6}$ (exactly 6 uppercase
             * alphanumeric characters)
             * --If validation fails, throws ConstraintViolationException
             * --Prevents SQL/NoSQL injection by restricting input to safe
             * characters
             * --WithoutIT: Invalid PNR formats could pass through;
             * potentially allowing injection attacks or malformed data.
             */
            @PathVariable @Pattern(regexp = "^[A-Z0-9]{6}$", message = "PNR must be exactly 6 alphanumeric characters (A-Z, 0-9)") String pnr) {
        log.info("Received request for PNR: {}", pnr);

        CompletableFuture<ResponseEntity<?>> future = new CompletableFuture<>();

        aggregatorService.aggregateBooking(pnr)
                .onSuccess(response -> {
                    log.info("Successfully processed booking for PNR: {}", pnr);
                    future.complete(ResponseEntity.ok(response));
                })
                .onFailure(error -> {
                    log.error("Error processing booking for PNR: {}", pnr, error);

                    if (error instanceof PNRNotFoundException) {
                        Map<String, Object> errorResponse = new HashMap<>();
                        errorResponse.put("error", "Not Found");
                        errorResponse.put("message", error.getMessage());
                        errorResponse.put("timestamp", Instant.now().toString());
                        future.complete(ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse));
                    } else if (error instanceof ServiceUnavailableException) {
                        Map<String, Object> errorResponse = new HashMap<>();
                        errorResponse.put("error", "Service Unavailable");
                        errorResponse.put("message",
                                "Booking service temporarily unavailable. Please try again later.");
                        errorResponse.put("timestamp", Instant.now().toString());
                        errorResponse.put("circuitBreakerState", "OPEN");
                        future.complete(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(errorResponse));
                    } else {
                        Map<String, Object> errorResponse = new HashMap<>();
                        errorResponse.put("error", "Internal Server Error");
                        errorResponse.put("message", "An unexpected error occurred");
                        errorResponse.put("timestamp", Instant.now().toString());
                        future.complete(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse));
                    }
                });

        return future;
    }

    /**
     * Handle validation exceptions (invalid PNR format)
     *
     * -@ExceptionHandler: Defines exception handling method for this controller
     * --Catches ConstraintViolationException thrown
     * by [@Validated] parameters
     * --Only handles exceptions from methods in this controller
     * --Allows custom error responses instead of default Spring
     * error page
     * --Returns HTTP 400 (Bad Request) with user-friendly error
     * message
     */
    @ExceptionHandler(jakarta.validation.ConstraintViolationException.class)
    public ResponseEntity<?> handleValidationException(jakarta.validation.ConstraintViolationException ex) {
        log.warn("Validation error: {}", ex.getMessage());
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("error", "Bad Request");
        errorResponse.put("message", "Invalid PNR format. PNR must be exactly 6 alphanumeric characters (A-Z, 0-9)");
        errorResponse.put("timestamp", Instant.now().toString());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    /**
     * -@ExceptionHandler(Exception.class): Global exception handler for this
     * controller
     * --Catches all uncaught exceptions from methods in this controller
     * --Acts as a fallback for exceptions not handled by specific handlers
     * --Returns HTTP 500 (Internal Server Error) for unexpected errors
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleException(Exception ex) {
        log.error("Unhandled exception", ex);
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("error", "Internal Server Error");
        errorResponse.put("message", ex.getMessage());
        errorResponse.put("timestamp", Instant.now().toString());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }
}
