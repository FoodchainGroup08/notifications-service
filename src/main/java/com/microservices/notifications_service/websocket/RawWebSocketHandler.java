package com.microservices.notifications_service.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

@Slf4j
public class RawWebSocketHandler extends TextWebSocketHandler {

    // key = branchId or orderId, value = set of sessions subscribed to it
    private final Map<String, Set<WebSocketSession>> sessions = new ConcurrentHashMap<>();
    private final String topicType; // "kitchen", "order", "manager"

    public RawWebSocketHandler(String topicType) {
        this.topicType = topicType;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String key = extractKey(session);
        sessions.computeIfAbsent(key, k -> new CopyOnWriteArraySet<>()).add(session);
        log.info("[{}] WebSocket connected: key={} sessionId={}", topicType, key, session.getId());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String key = extractKey(session);
        Set<WebSocketSession> group = sessions.get(key);
        if (group != null) {
            group.remove(session);
            if (group.isEmpty()) sessions.remove(key);
        }
        log.info("[{}] WebSocket closed: key={} sessionId={}", topicType, key, session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        // Client pings — echo back or ignore
        log.debug("[{}] Received message from client: {}", topicType, message.getPayload());
    }

    public void broadcast(String key, String jsonPayload) {
        Set<WebSocketSession> group = sessions.get(key);
        if (group == null || group.isEmpty()) return;
        TextMessage message = new TextMessage(jsonPayload);
        group.forEach(session -> {
            try {
                if (session.isOpen()) session.sendMessage(message);
            } catch (IOException e) {
                log.warn("[{}] Failed to send WS message to session {}: {}", topicType, session.getId(), e.getMessage());
            }
        });
    }

    private String extractKey(WebSocketSession session) {
        // URI pattern: /ws/kitchen/{branchId}, /ws/orders/{orderId}, /ws/manager/{branchId}
        String path = session.getUri() != null ? session.getUri().getPath() : "";
        String[] parts = path.split("/");
        return parts.length > 0 ? parts[parts.length - 1] : "default";
    }

    public int getConnectedCount(String key) {
        Set<WebSocketSession> group = sessions.get(key);
        return group != null ? group.size() : 0;
    }
}
