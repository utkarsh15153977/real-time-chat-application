package com.chatApplication.message_service.service;

import com.chatApplication.message_service.config.SecurityLogUtils;
import com.chatApplication.message_service.config.WebSocketSessionExpiryManager;
import com.chatApplication.message_service.entity.UserPresence;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import com.chatApplication.message_service.repository.UserPresenceRepository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Listens for WebSocket STOMP session lifecycle events.
 * <p>
 * Security responsibilities:
 *   1. Extract userId from authenticated Principal (not client headers)
 *   2. Register sessions with WebSocketSessionExpiryManager for expiration
 *   3. Clean up presence state on disconnect
 *   4. Track session activity for heartbeat monitoring
 * <p>
 * Session expiration:
 *   - Token expiry is stored in session attributes during CONNECT
 *   - WebSocketSessionExpiryManager periodically checks and closes expired sessions
 *   - This prevents stale sessions from remaining active after token expiry
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketEventListener {

    private final PresenceTracker presenceTracker;
    private final UserPresenceRepository userPresenceRepository;
    private final WebSocketSessionExpiryManager expiryManager;

    @EventListener
    public void handleConnect(SessionConnectEvent event) {
        StompHeaderAccessor accessor =
                StompHeaderAccessor.wrap(event.getMessage());

        // Extract userId from authenticated Principal (set by WebSocketAuthInterceptor)
        String userId = extractUserIdFromPrincipal(accessor);

        if (userId != null) {
            presenceTracker.userOnline(userId);

            // Register session for expiration tracking
            Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
            Instant tokenExp = (Instant) sessionAttributes.get("token_exp");
            if (tokenExp != null) {
                expiryManager.registerSession(accessor.getSessionId(), tokenExp);
            }

            log.debug("User {} marked online via WebSocket connect", userId);
        }
    }

    @EventListener
    public void handleDisconnect(SessionDisconnectEvent event) {
        StompHeaderAccessor accessor =
                StompHeaderAccessor.wrap(event.getMessage());

        // Extract userId from authenticated Principal
        String userId = extractUserIdFromPrincipal(accessor);

        if (userId == null) {
            return;
        }

        presenceTracker.userOffline(userId);

        // Remove session from expiry tracking
        expiryManager.removeSession(accessor.getSessionId());

        UserPresence presence = userPresenceRepository
                .findById(userId)
                .orElse(new UserPresence());

        presence.setUserId(userId);
        presence.setOnline(false);
        presence.setLastSeen(LocalDateTime.now());

        userPresenceRepository.save(presence);

        log.debug("User {} marked offline via WebSocket disconnect", userId);
    }

    /**
     * Extracts userId from the authenticated Principal on the StompHeaderAccessor.
     * Falls back to X-User-Id header (from Gateway) if Principal is not set.
     *
     * @param accessor the STOMP header accessor
     * @return the authenticated userId, or null if not available
     */
    private String extractUserIdFromPrincipal(StompHeaderAccessor accessor) {
        // Primary: extract from authenticated Principal
        if (accessor.getUser() instanceof UsernamePasswordAuthenticationToken auth) {
            return auth.getName();
        }

        // Fallback: extract from X-User-Id header (Gateway-injected)
        String forwardedUserId = accessor.getFirstNativeHeader("X-User-Id");
        if (forwardedUserId != null && !forwardedUserId.isBlank()) {
            return forwardedUserId;
        }

        return null;
    }
}
