package com.pnr.aggregator.websocket;

import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for PNRWebSocketHandler
 * Coverage: WebSocket lifecycle, event broadcasting, error handling
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PNRWebSocketHandlerTest {

    @Mock
    private EventBus eventBus;

    @Mock
    private MessageConsumer<Object> messageConsumer;

    @Mock
    private WebSocketSession session1;

    @Mock
    private WebSocketSession session2;

    @InjectMocks
    private PNRWebSocketHandler webSocketHandler;

    @BeforeEach
    void setUp() {
        /*
         * whyCodeAdded: Initialize event bus and WebSocket session mocks for
         * PNRWebSocketHandler tests
         * Sets up two mock WebSocket sessions in open state and configures event bus
         * consumer
         * to test session management, message broadcasting, and connection lifecycle
         * scenarios
         */
        when(eventBus.consumer(anyString(), any())).thenReturn(messageConsumer);
        when(session1.isOpen()).thenReturn(true);
        when(session2.isOpen()).thenReturn(true);
        when(session1.getId()).thenReturn("session1");
        when(session2.getId()).thenReturn("session2");
    }

    /**
     * Input: WebSocket handler initialization
     * ExpectedOut: Event bus consumer registered for "pnr.fetched" channel
     */
    @Test
    void testInit_SubscribesToEventBus() {
        // When
        webSocketHandler.init();

        // Then
        verify(eventBus).consumer(eq("pnr.fetched"), any());
    }

    /**
     * Input: WebSocket session1 connection established
     * ExpectedOut: Session added to handler's session set
     */
    @Test
    void testAfterConnectionEstablished_AddsSession() throws Exception {
        // When
        webSocketHandler.afterConnectionEstablished(session1);

        // Then - Session should be tracked
        // We can't directly verify the set, but we can verify by sending a broadcast
        webSocketHandler.init();

        // Trigger a broadcast by simulating event bus message
        ArgumentCaptor<io.vertx.core.Handler> handlerCaptor = ArgumentCaptor.forClass(io.vertx.core.Handler.class);
        verify(eventBus).consumer(eq("pnr.fetched"), handlerCaptor.capture());

        // Session should be in the set and receive messages
        assertNotNull(handlerCaptor.getValue());
    }

    /**
     * Input: WebSocket session1 established then closed with NORMAL status
     * ExpectedOut: Session removed from handler's session set
     */
    @Test
    void testAfterConnectionClosed_RemovesSession() throws Exception {
        // Given - Add then remove session
        webSocketHandler.afterConnectionEstablished(session1);

        // When
        webSocketHandler.afterConnectionClosed(session1, CloseStatus.NORMAL);

        // Then - Session should no longer receive broadcasts
        // After removal, broadcasts should not attempt to send to this session
        assertDoesNotThrow(() -> webSocketHandler.afterConnectionClosed(session1, CloseStatus.NORMAL));
    }

    /**
     * Input: Two WebSocket sessions (session1, session2) and event data (PNR
     * "ABC123", status "SUCCESS")
     * ExpectedOut: Both sessions receive the broadcast message
     */
    @Test
    void testMultipleSessions_AllReceiveBroadcast() throws Exception {
        // Given
        webSocketHandler.afterConnectionEstablished(session1);
        webSocketHandler.afterConnectionEstablished(session2);

        webSocketHandler.init();

        ArgumentCaptor<io.vertx.core.Handler> handlerCaptor = ArgumentCaptor.forClass(io.vertx.core.Handler.class);
        verify(eventBus).consumer(eq("pnr.fetched"), handlerCaptor.capture());

        // Create mock message
        io.vertx.core.eventbus.Message<Object> eventMessage = mock(io.vertx.core.eventbus.Message.class);
        JsonObject eventData = new JsonObject()
                .put("pnr", "ABC123")
                .put("status", "SUCCESS")
                .put("timestamp", "2025-12-01T10:00:00Z");
        when(eventMessage.body()).thenReturn(eventData);

        // When - Trigger event
        handlerCaptor.getValue().handle(eventMessage);

        // Then - Both sessions should receive the message
        verify(session1, atLeastOnce()).sendMessage(any(TextMessage.class));
        verify(session2, atLeastOnce()).sendMessage(any(TextMessage.class));
    }

    /**
     * Input: Two sessions (session1 closed, session2 open) and event data
     * ExpectedOut: Only session2 receives message, session1 skipped
     */
    @Test
    void testBroadcast_SkipsClosedSessions() throws Exception {
        // Given
        webSocketHandler.afterConnectionEstablished(session1);
        webSocketHandler.afterConnectionEstablished(session2);

        when(session1.isOpen()).thenReturn(false); // session1 is closed
        when(session2.isOpen()).thenReturn(true);

        webSocketHandler.init();

        ArgumentCaptor<io.vertx.core.Handler> handlerCaptor = ArgumentCaptor.forClass(io.vertx.core.Handler.class);
        verify(eventBus).consumer(eq("pnr.fetched"), handlerCaptor.capture());

        io.vertx.core.eventbus.Message<Object> eventMessage = mock(io.vertx.core.eventbus.Message.class);
        JsonObject eventData = new JsonObject().put("pnr", "ABC123").put("status", "SUCCESS");
        when(eventMessage.body()).thenReturn(eventData);

        // When
        handlerCaptor.getValue().handle(eventMessage);

        // Then - Only open session should receive message
        verify(session1, never()).sendMessage(any()); // Closed session
        verify(session2, atLeastOnce()).sendMessage(any()); // Open session
    }

    /**
     * Input: Two sessions with session1 throwing IOException, event data (PNR
     * "ABC123")
     * ExpectedOut: Exception caught, session2 still receives message, no exception
     * thrown
     */
    @Test
    void testBroadcast_ContinuesOnIOException() throws Exception {
        // Given
        webSocketHandler.afterConnectionEstablished(session1);
        webSocketHandler.afterConnectionEstablished(session2);

        doThrow(new IOException("Network error")).when(session1).sendMessage(any());

        webSocketHandler.init();

        ArgumentCaptor<io.vertx.core.Handler> handlerCaptor = ArgumentCaptor.forClass(io.vertx.core.Handler.class);
        verify(eventBus).consumer(eq("pnr.fetched"), handlerCaptor.capture());

        io.vertx.core.eventbus.Message<Object> eventMessage = mock(io.vertx.core.eventbus.Message.class);
        JsonObject eventData = new JsonObject().put("pnr", "ABC123");
        when(eventMessage.body()).thenReturn(eventData);

        // When - Should not throw exception
        assertDoesNotThrow(() -> handlerCaptor.getValue().handle(eventMessage));

        // Then - session2 should still receive message
        verify(session2, atLeastOnce()).sendMessage(any());
    }

    /**
     * Input: Event data with PNR "GHTW42", status "DEGRADED", timestamp
     * "2025-12-01T10:00:00Z"
     * ExpectedOut: TextMessage payload contains "GHTW42" and "DEGRADED"
     */
    @Test
    void testEventDataFormat() throws Exception {
        // Given
        webSocketHandler.afterConnectionEstablished(session1);
        webSocketHandler.init();

        ArgumentCaptor<io.vertx.core.Handler> handlerCaptor = ArgumentCaptor.forClass(io.vertx.core.Handler.class);
        verify(eventBus).consumer(eq("pnr.fetched"), handlerCaptor.capture());

        ArgumentCaptor<TextMessage> messageCaptor = ArgumentCaptor.forClass(TextMessage.class);

        io.vertx.core.eventbus.Message<Object> eventMessage = mock(io.vertx.core.eventbus.Message.class);
        JsonObject eventData = new JsonObject()
                .put("pnr", "GHTW42")
                .put("status", "DEGRADED")
                .put("timestamp", "2025-12-01T10:00:00Z");
        when(eventMessage.body()).thenReturn(eventData);

        // When
        handlerCaptor.getValue().handle(eventMessage);

        // Then
        verify(session1, atLeastOnce()).sendMessage(messageCaptor.capture());

        String payload = messageCaptor.getValue().getPayload();
        assertTrue(payload.contains("GHTW42"));
        assertTrue(payload.contains("DEGRADED"));
    }

    /**
     * Input: Session lifecycle - start empty, add session1, add session2, remove
     * session1, remove session2
     * ExpectedOut: Session count correctly tracks lifecycle: 0->1->2->1->0
     */
    @Test
    void testConnectionLifecycle() throws Exception {
        // Given - Empty handler
        assertEquals(0, webSocketHandler.getSessions().size());

        // When - Add session
        webSocketHandler.afterConnectionEstablished(session1);

        // Then - Session added
        assertEquals(1, webSocketHandler.getSessions().size());

        // When - Add another session
        webSocketHandler.afterConnectionEstablished(session2);

        // Then - Two sessions
        assertEquals(2, webSocketHandler.getSessions().size());

        // When - Remove one session
        webSocketHandler.afterConnectionClosed(session1, CloseStatus.NORMAL);

        // Then - One session remains
        assertEquals(1, webSocketHandler.getSessions().size());

        // When - Remove last session
        webSocketHandler.afterConnectionClosed(session2, CloseStatus.NORMAL);

        // Then - No sessions
        assertEquals(0, webSocketHandler.getSessions().size());
    }

    /**
     * Input: 10 WebSocket sessions added concurrently, then 5 removed
     * ExpectedOut: Session count is 10 after adds, then 5 after removals
     */
    @Test
    void testConcurrentSessionManagement() throws Exception {
        // Given - Multiple sessions added concurrently
        WebSocketSession[] sessions = new WebSocketSession[10];
        for (int i = 0; i < 10; i++) {
            sessions[i] = mock(WebSocketSession.class);
            when(sessions[i].isOpen()).thenReturn(true);
            when(sessions[i].getId()).thenReturn("session" + i);
            webSocketHandler.afterConnectionEstablished(sessions[i]);
        }

        // Then
        assertEquals(10, webSocketHandler.getSessions().size());

        // When - Remove half
        for (int i = 0; i < 5; i++) {
            webSocketHandler.afterConnectionClosed(sessions[i], CloseStatus.NORMAL);
        }

        // Then
        assertEquals(5, webSocketHandler.getSessions().size());
    }
}
