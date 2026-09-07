package com.chatApplication.chat_service.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Listens for WebSocket STOMP session lifecycle events and updates
 * user presence status in Redis.
 * <p>
 * On CONNECT: Marks user as online (creates Redis key with 30s TTL).
 * On DISCONNECT: Marks user as offline (deletes Redis key).
 * <p>
 * The session-to-user mapping is maintained in memory so that on
 * disconnect we know which user to mark offline without requiring
 * the client to send an explicit disconnect message.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PresenceWebSocketEventListener {

    private final PresenceService presenceService;

    /**
     * Maps STOMP session IDs to user IDs for disconnect handling.
     * Uses ConcurrentHashMap for thread safety.
     */
    private final Map<String, Long> sessionUserMap =
            new ConcurrentHashMap<>();

    /**
     * Handles STOMP CONNECT events.
     * Extracts the userId from the "userId" native header (set by the client
     * during the WebSocket handshake) and marks the user as online.
     *
     * @param event the Spring session connect event
     */
    @EventListener
    public void handleSessionConnected(SessionConnectEvent event) {
        SimpMessageHeaderAccessor accessor =
                SimpMessageHeaderAccessor.wrap(event.getMessage());

        String userIdStr = accessor.getFirstNativeHeader("userId");
        String sessionId = accessor.getSessionId();

        if (userIdStr != null && sessionId != null) {
            try {
                Long userId = Long.parseLong(userIdStr);

                // Track session-to-user mapping for disconnect handling
                sessionUserMap.put(sessionId, userId);

                // Mark user online in Redis (30s TTL)
                presenceService.setUserOnline(userId);

                log.info("WebSocket connected: userId={}, sessionId={}",
                        userId, sessionId);

            } catch (NumberFormatException e) {
                log.warn("Invalid userId header in CONNECT frame: {}",
                        userIdStr);
            }
        }
    }

    /**
     * Handles STOMP DISCONNECT events.
     * Removes the session mapping and marks the user as offline.
     *
     * @param event the Spring session disconnect event
     */
    @EventListener
    public void handleSessionDisconnect(SessionDisconnectEvent event) {
        String sessionId = event.getSessionId();
        Long userId = sessionUserMap.remove(sessionId);

        if (userId != null) {
            // Mark user offline in Redis (delete key)
            presenceService.setUserOffline(userId);

            log.info("WebSocket disconnected: userId={}, sessionId={}",
                    userId, sessionId);
        }
    }
}
