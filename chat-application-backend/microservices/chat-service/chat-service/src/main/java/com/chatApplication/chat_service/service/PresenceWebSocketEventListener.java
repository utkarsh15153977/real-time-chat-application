package com.chatApplication.chat_service.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Listens for WebSocket STOMP session lifecycle events and updates
 * user presence status in Redis.
 * <p>
 * On CONNECT: Extracts userId from the authenticated Principal (set by
 * WebSocketAuthInterceptor) and marks the user as online.
 * On DISCONNECT: Marks user as offline.
 * <p>
 * SECURITY: Never trusts client-supplied "userId" native header.
 * The Principal is established exclusively by WebSocketAuthInterceptor
 * after JWT validation.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PresenceWebSocketEventListener {

    private final PresenceService presenceService;

    private final Map<String, Long> sessionUserMap =
            new ConcurrentHashMap<>();

    @EventListener
    public void handleSessionConnected(SessionConnectEvent event) {
        SimpMessageHeaderAccessor accessor =
                SimpMessageHeaderAccessor.wrap(event.getMessage());

        String sessionId = accessor.getSessionId();
        if (sessionId == null) {
            log.warn("WebSocket CONNECT without session ID");
            return;
        }

        String userIdStr = extractUserIdFromPrincipal(accessor);

        if (userIdStr == null) {
            userIdStr = extractUserIdFromSessionAttributes(accessor);
        }

        if (userIdStr == null) {
            log.warn("WebSocket CONNECT without authenticated identity: sessionId={}", sessionId);
            return;
        }

        try {
            Long userId = Long.parseLong(userIdStr);
            sessionUserMap.put(sessionId, userId);
            presenceService.setUserOnline(userId);
            log.info("WebSocket connected: userId={}, sessionId={}", userId, sessionId);
        } catch (NumberFormatException e) {
            log.warn("Invalid userId: {} for session {}", userIdStr, sessionId);
        }
    }

    @EventListener
    public void handleSessionDisconnect(SessionDisconnectEvent event) {
        String sessionId = event.getSessionId();
        Long userId = sessionUserMap.remove(sessionId);

        if (userId != null) {
            presenceService.setUserOffline(userId);
            log.info("WebSocket disconnected: userId={}, sessionId={}", userId, sessionId);
        }
    }

    private String extractUserIdFromPrincipal(SimpMessageHeaderAccessor accessor) {
        Principal principal = accessor.getUser();
        if (principal != null) {
            return principal.getName();
        }
        return null;
    }

    private String extractUserIdFromSessionAttributes(SimpMessageHeaderAccessor accessor) {
        Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
        if (sessionAttributes != null) {
            Object userIdObj = sessionAttributes.get("user_id");
            if (userIdObj instanceof String userIdStr && !userIdStr.isBlank()) {
                log.debug("Resolved userId from session attributes for session {}",
                        accessor.getSessionId());
                return userIdStr;
            }
        }
        return null;
    }
}
