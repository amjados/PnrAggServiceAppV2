package com.pnr.aggregator.service;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;

/**
 * @Service: Marks this class as a Spring service component
 * - Registers as a Spring bean for automatic discovery and injection
 * - Contains event bus consumer logic for PNR events
 */
@Service
/**
 * @Slf4j: Lombok annotation for SLF4J logger generation
 * - Auto-generates: private static final Logger log = LoggerFactory.getLogger(PnrEventConsumer.class)
 */
@Slf4j
public class PnrEventConsumer {

    /**
     * @Autowired: Dependency injection for Vert.x instance
     * - Injects Vert.x configured in VertxConfig
     * - Provides access to the event bus for async messaging
     */
    @Autowired
    private Vertx vertx;

    /**
     * @PostConstruct: Initialization method called after bean creation
     * - Executes automatically after dependency injection
     * - Registers event bus consumer for "pnr.fetched" events
     * - Ensures consumer is ready before application starts handling requests
     */
    @PostConstruct
    public void registerEventBusConsumer() {
        vertx.eventBus().consumer("pnr.fetched", message -> {
            JsonObject payload = (JsonObject) message.body();
            String pnr = payload.getString("pnr");
            String status = payload.getString("status");
            String timestamp = payload.getString("timestamp");

            log.info("[EventBus] PNR {} fetched with status {} at {}", pnr, status, timestamp);
        });

        log.info("PnrEventConsumer registered to listen on 'pnr.fetched' address");
    }
}
