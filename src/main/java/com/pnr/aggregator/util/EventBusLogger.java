package com.pnr.aggregator.util;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

/**
 * Event Bus Logger
 * 
 * Logs all events published to the Vert.x EventBus for monitoring and debugging.
 * Subscribes to EventBus topics for application events.
 * - "pnr.fetched": PNR fetch events from BookingAggregatorService
 * 
 * Event Types:
 * - PNR Fetch: Contains pnr, status, timestamp
 * - Retry Events: Contains type=RETRY, service, status, message, timestamp
 */
/**
 * -@Component: Registers this class as a Spring component.
 * --Makes this a Spring-managed bean
 * --Enables automatic discovery during component scanning
 * --Contains event bus consumer logic for all EventBus events
 * --WithoutIT: This class won't be instantiated by Spring;
 * ---event bus consumer logging won't be configured.
 * =========
 * -@Slf4j: Lombok annotation for SLF4J logger generation
 * --Auto-generates: private static final Logger log =
 * LoggerFactory.getLogger(EventBusLogger.class)
 * --WithoutIT: No logger available;
 * ---compilation errors on log statements.
 */
@Component
@Slf4j
public class EventBusLogger {

    /**
     * -@Autowired: Dependency injection for Vert.x instance
     * --Injects Vert.x configured in VertxConfig
     * --Provides access to the event bus for async messaging
     * --WithoutIT: vertx would be null;
     * ---event bus consumer registration would fail.
     */
    @Autowired
    private Vertx vertx;

    /**
     * -@PostConstruct: Initialization method called after bean creation.
     * --Executes automatically after dependency injection
     * --Registers event bus consumers for "pnr.fetched" and "pnr.retryFetched"
     * --Logs PNR fetch events and retry events separately
     * --Ensures consumers are ready before application starts handling requests
     * --WithoutIT: registerEventBusConsumer() won't be called automatically;
     * ---event bus consumers wouldn't be registered.
     */
    @PostConstruct
    public void registerEventBusConsumer() {
        // Subscribe to PNR fetch events
        vertx.eventBus().consumer("pnr.fetched", message -> {
            JsonObject payload = (JsonObject) message.body();
            String pnr = payload.getString("pnr");
            String status = payload.getString("status");
            String timestamp = payload.getString("timestamp");

            log.info("[EventBus-PNR] PNR {} fetched with status {} at {}", pnr, status, timestamp);
        });

        log.info("EventBusLogger registered to listen on 'pnr.fetched' address");
    }
}
