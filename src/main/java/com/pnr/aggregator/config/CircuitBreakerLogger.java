package com.pnr.aggregator.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.circuitbreaker.event.CircuitBreakerOnErrorEvent;
import io.github.resilience4j.circuitbreaker.event.CircuitBreakerOnSuccessEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

/**
 * Circuit Breaker Event Logger
 * 
 * Logs detailed information about circuit breaker state changes, metrics,
 * and calculations to help diagnose why circuits are opening.
 */
/**
 * -@Component: Registers this class as a Spring component
 * --Makes this a Spring-managed bean
 * --Enables automatic discovery during component scanning
 * --Allows dependency injection and lifecycle management
 * =========
 * -@Slf4j: Lombok annotation for logger generation
 * --Creates: private static final Logger log =
 * LoggerFactory.getLogger(CircuitBreakerLogger.class)
 * --Enables logging of circuit breaker events
 */
@Component
@Slf4j
public class CircuitBreakerLogger {

    /**
     * -@Autowired: Dependency injection for CircuitBreakerRegistry
     * --Injects registry containing all circuit breakers in the application
     * --Used to attach event listeners to all circuit breakers
     */
    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    /**
     * -@PostConstruct: Initialization method for event listener registration
     * --Called automatically after dependency injection completes
     * --Registers event listeners on all circuit breakers
     * --Enables monitoring of circuit breaker state changes and metrics
     */
    @PostConstruct
    public void registerEventListeners() {
        circuitBreakerRegistry.getAllCircuitBreakers().forEach(this::registerListeners);
    }

    private void registerListeners(CircuitBreaker circuitBreaker) {
        String cbName = circuitBreaker.getName();

        // Log every successful call with current metrics
        circuitBreaker.getEventPublisher().onSuccess(event -> {
            logMetrics(cbName, event, "SUCCESS");
        });

        // Log every error with current metrics
        circuitBreaker.getEventPublisher().onError(event -> {
            logMetrics(cbName, event, "ERROR");
        });

        // Log when failure rate threshold is exceeded
        circuitBreaker.getEventPublisher().onFailureRateExceeded(event -> {
            log.warn("[{}] FAILURE RATE EXCEEDED! Current: {}%, Threshold: {}%",
                    cbName,
                    String.format("%.2f", event.getFailureRate()),
                    String.format("%.2f", circuitBreaker.getCircuitBreakerConfig().getFailureRateThreshold()));
            logDetailedMetrics(circuitBreaker);
        });

        // Log state transitions (CLOSED -> OPEN -> HALF_OPEN)
        circuitBreaker.getEventPublisher().onStateTransition(event -> {
            log.warn("[{}] STATE TRANSITION: {} -> {}",
                    cbName,
                    event.getStateTransition().getFromState(),
                    event.getStateTransition().getToState());
            logDetailedMetrics(circuitBreaker);
        });

        // Log when calls are rejected because circuit is open
        circuitBreaker.getEventPublisher().onCallNotPermitted(event -> {
            log.warn("[{}] CALL REJECTED - Circuit is OPEN", cbName);
            logDetailedMetrics(circuitBreaker);
        });
    }

    private void logMetrics(String cbName, CircuitBreakerOnSuccessEvent event, String outcome) {
        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker(cbName);
        CircuitBreaker.Metrics metrics = cb.getMetrics();

        log.info("[{}] {} | State: {} | Failures: {}/{} | Failure Rate: {}% | Threshold: {}%",
                cbName,
                outcome,
                cb.getState(),
                metrics.getNumberOfFailedCalls(),
                metrics.getNumberOfBufferedCalls(),
                String.format("%.2f", metrics.getFailureRate()),
                String.format("%.2f", cb.getCircuitBreakerConfig().getFailureRateThreshold()));
    }

    private void logMetrics(String cbName, CircuitBreakerOnErrorEvent event, String outcome) {
        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker(cbName);
        CircuitBreaker.Metrics metrics = cb.getMetrics();

        log.error("[{}] {} | State: {} | Failures: {}/{} | Failure Rate: {}% | Threshold: {}% | Error: {}",
                cbName,
                outcome,
                cb.getState(),
                metrics.getNumberOfFailedCalls(),
                metrics.getNumberOfBufferedCalls(),
                String.format("%.2f", metrics.getFailureRate()),
                String.format("%.2f", cb.getCircuitBreakerConfig().getFailureRateThreshold()),
                event.getThrowable().getClass().getSimpleName());
    }

    private void logDetailedMetrics(CircuitBreaker circuitBreaker) {
        CircuitBreaker.Metrics metrics = circuitBreaker.getMetrics();
        var config = circuitBreaker.getCircuitBreakerConfig();

        log.warn("[{}] DETAILED METRICS:", circuitBreaker.getName());
        log.warn("   Current State: {}", circuitBreaker.getState());
        log.warn("   Buffered Calls: {}", metrics.getNumberOfBufferedCalls());
        log.warn("   Failed Calls: {}", metrics.getNumberOfFailedCalls());
        log.warn("   Success Calls: {}", metrics.getNumberOfSuccessfulCalls());
        log.warn("   Not Permitted: {}", metrics.getNumberOfNotPermittedCalls());
        log.warn("   Failure Rate: {}%", String.format("%.2f", metrics.getFailureRate()));
        log.warn("   Slow Call Rate: {}%", String.format("%.2f", metrics.getSlowCallRate()));
        log.warn("   Config - Sliding Window Size: {}", config.getSlidingWindowSize());
        log.warn("   Config - Min Calls: {}", config.getMinimumNumberOfCalls());
        log.warn("   Config - Failure Threshold: {}%", config.getFailureRateThreshold());
        log.warn("   Config - Wait Duration: {}s", config.getWaitIntervalFunctionInOpenState().apply(1) / 1000);
    }
}
