package com.pnr.aggregator.config;

import com.pnr.aggregator.websocket.PNRWebSocketHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocket Configuration
 * 
 * WHY: Enables WebSocket support for real-time bidirectional communication
 * USE CASE: Allows clients to subscribe to PNR fetch events and receive real-time updates
 * 
 * CONFIGURATION:
 * - Endpoint: /ws/pnr
 * - Allowed Origins: * (all origins for development; restrict in production)
 * 
 * FLOW:
 * 1. Client connects to ws://localhost:8080/ws/pnr
 * 2. PNRWebSocketHandler manages the connection
 * 3. When BookingAggregatorService publishes to EventBus, handler broadcasts to all clients
 * 
 * ADVANTAGE: Real-time updates without polling, reduces server load
 */
/**
 * -@Configuration: Marks this class as a source of bean definitions
 * --Indicates this is a Spring configuration class
 * --Spring will process this class to configure WebSocket support
 * --WithoutIT: WebSocket configuration won't be processed;
 * WebSocket endpoints won't be registered.
 * =========
 * -@EnableWebSocket: Enables WebSocket message handling
 * --Activates Spring's WebSocket support infrastructure
 * --Required for implementing WebSocketConfigurer
 * --Enables endpoints for bidirectional client-server communication
 * --WithoutIT: WebSocket infrastructure won't be initialized;
 * clients can't connect to WebSocket endpoints, breaking real-time updates.
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    /**
     * -@Autowired: Dependency injection for WebSocket handler
     * --Injects PNRWebSocketHandler component to handle WebSocket connections
     * --Handler manages session lifecycle and message broadcasting
     * --WithoutIT: pnrWebSocketHandler would be null;
     * WebSocket endpoint registration would fail with NullPointerException.
     */
    @Autowired
    private PNRWebSocketHandler pnrWebSocketHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(pnrWebSocketHandler, "/ws/pnr")
                .setAllowedOrigins("*");
    }
}
