package com.pnr.aggregator.websocket;

import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonObject;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket Handler for PNR Real-Time Updates
 * 
 * WHY: Manages WebSocket connections and broadcasts PNR fetch events to connected clients
 * USE CASE: Enables real-time notifications when PNR data is fetched
 * 
 * ARCHITECTURE:
 * 1. Maintains a thread-safe set of active WebSocket sessions
 * 2. Subscribes to Vert.x EventBus "pnr.fetched" topic on initialization
 * 3. When event received, broadcasts JSON data to all connected clients
 * 
 * EVENT FLOW:
 * 1. BookingAggregatorService publishes to EventBus: "pnr.fetched"
 * 2. This handler receives the event with PNR data
 * 3. Handler broadcasts to all active WebSocket sessions
 * 4. Clients receive real-time update
 * 
 * DATA FORMAT:
 * {
 *   "pnr": "GHTW42",
 *   "status": "SUCCESS",
 *   "timestamp": 1732701234567
 * }
 * 
 * THREAD SAFETY: Uses ConcurrentHashMap.newKeySet() for concurrent session management
 * ERROR HANDLING: Catches IOException during broadcast, continues with other sessions
 */
/**
 * -@Component: Marks this class as a Spring component
 * --Registers this as a Spring-managed bean
 * --Makes it available for dependency injection
 * --Discovered during component scanning
 * --Used in WebSocketConfig to register the handler
 * --WithoutIT: Handler won't be discovered;
 * WebSocket endpoints wouldn't work.
 */
@Component
public class PNRWebSocketHandler extends TextWebSocketHandler {

    private final Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();

    /**
     * -@Autowired: Dependency injection for Vert.x EventBus
     * --Injects EventBus configured in VertxConfig
     * --Enables subscription to PNR events
     * --Bridges Vert.x event bus with WebSocket communication
     * --WithoutIT: eventBus would be null;
     * real-time updates wouldn't be broadcast to clients.
     */
    @Autowired
    private EventBus eventBus;

    /**
     * -@PostConstruct: Initialization method executed after dependency injection
     * --Runs automatically after [@Autowired] fields are populated
     * --Executes once during bean lifecycle, before handling requests
     * --Subscribes to "pnr.fetched" topic on event bus
     * --Ensures consumer is ready when application starts
     * --WithoutIT: init() won't be called automatically;
     * event bus consumer wouldn't be registered, no real-time updates.
     */
    @PostConstruct
    public void init() {
        eventBus.consumer("pnr.fetched", message -> {
            JsonObject data = (JsonObject) message.body();
            broadcast(data.encode());
        });
    }

    /**
     * Called when a new WebSocket connection is established
     * Adds the session to the active sessions set
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.add(session);
    }

    /**
     * Called when a WebSocket connection is closed
     * Removes the session from the active sessions set
     */
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
    }

    /**
     * Broadcasts a message to all active WebSocket sessions
     * Skips closed sessions and handles IOException gracefully
     * 
     * -@param message JSON string to broadcast
     */
    private void broadcast(String message) {
        sessions.forEach(session -> {
            try {
                if (session.isOpen()) {
                    session.sendMessage(new TextMessage(message));
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }
}
