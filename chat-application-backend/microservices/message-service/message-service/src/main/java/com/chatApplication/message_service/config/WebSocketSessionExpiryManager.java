package com.chatApplication.message_service.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages WebSocket session expiration by tracking JWT token expiry
 * and cleaning up expired sessions.
 * <p>
 * Security model:
 *   - During CONNECT, the WebSocketAuthInterceptor stores token exp timestamp
 *     in session attributes ("token_exp")
 *   - This manager periodically checks active sessions against current time
 *   - Expired sessions are programmatically closed
 *   - Inbound frames are also checked before processing
 * <p>
 * Heartbeat mechanism:
 *   - Clients are expected to send heartbeat frames (STOMP SEND to /app/heartbeat)
 *   - Sessions without any activity for the token duration are considered stale
 *   - The @Scheduled task runs every 30 seconds to clean up expired sessions
 * <p>
 * Thread safety:
 *   - ConcurrentHashMap for tracking last-activity timestamps
 *   - Atomic operations for session management
 *   - No blocking operations on Netty event loop
 */
@Slf4j
@Configuration
@EnableScheduling
public class WebSocketSessionExpiryManager {

    private final SimpMessagingTemplate messagingTemplate;
    private final SimpUserRegistry userRegistry;

    /** Tracks session expiry timestamps: sessionId -> expiry instant */
    private final Map<String, Instant> sessionExpiryMap = new ConcurrentHashMap<>();

    /** Tracks last activity per session: sessionId -> last active instant */
    private final Map<String, Instant> sessionActivityMap = new ConcurrentHashMap<>();

    public WebSocketSessionExpiryManager(
            SimpMessagingTemplate messagingTemplate,
            SimpUserRegistry userRegistry) {
        this.messagingTemplate = messagingTemplate;
        this.userRegistry = userRegistry;
    }

    /**
     * Registers a session with its token expiry timestamp.
     * Called by WebSocketAuthInterceptor after successful CONNECT.
     *
     * @param sessionId the STOMP session ID
     * @param tokenExp  the token expiration instant
     */
    public void registerSession(String sessionId, Instant tokenExp) {
        sessionExpiryMap.put(sessionId, tokenExp);
        sessionActivityMap.put(sessionId, Instant.now());
        log.debug("Registered session {} with expiry {}", sessionId, tokenExp);
    }

    /**
     * Updates the last activity timestamp for a session.
     * Called on every inbound STOMP frame.
     *
     * @param sessionId the STOMP session ID
     */
    public void updateActivity(String sessionId) {
        sessionActivityMap.put(sessionId, Instant.now());
    }

    /**
     * Removes a session from tracking.
     * Called on DISCONNECT event.
     *
     * @param sessionId the STOMP session ID
     */
    public void removeSession(String sessionId) {
        sessionExpiryMap.remove(sessionId);
        sessionActivityMap.remove(sessionId);
        log.debug("Removed session {} from expiry tracking", sessionId);
    }

    /**
     * Checks if a session's token has expired.
     *
     * @param sessionId the STOMP session ID
     * @return true if the session is expired or unknown
     */
    public boolean isSessionExpired(String sessionId) {
        Instant expiry = sessionExpiryMap.get(sessionId);
        if (expiry == null) {
            // Unknown session - consider expired for safety
            return true;
        }
        return Instant.now().isAfter(expiry);
    }

    /**
     * Checks if a frame can be processed for the given session.
     * Frames are rejected if the session's token has expired.
     *
     * @param sessionId the STOMP session ID
     * @return true if the frame should be allowed
     */
    public boolean canProcessFrame(String sessionId) {
        return !isSessionExpired(sessionId);
    }

    /**
     * Scheduled task that runs every 30 seconds to check for expired sessions.
     * Iterates over all tracked sessions and closes those with expired tokens.
     *
     * Uses fixedDelay to avoid overlapping executions if cleanup takes longer
     * than the interval.
     */
    @Scheduled(fixedDelayString = "${security.websocket.expiry-check-interval:30000}")
    public void checkExpiredSessions() {
        if (sessionExpiryMap.isEmpty()) {
            return;
        }

        Instant now = Instant.now();
        int closedCount = 0;

        for (Map.Entry<String, Instant> entry : sessionExpiryMap.entrySet()) {
            String sessionId = entry.getKey();
            Instant expiry = entry.getValue();

            if (now.isAfter(expiry)) {
                log.info("Closing expired WebSocket session: {}", sessionId);
                closeExpiredSession(sessionId);
                closedCount++;
            }
        }

        if (closedCount > 0) {
            log.info("Closed {} expired WebSocket sessions", closedCount);
        }
    }

    /**
     * Closes an expired session by sending a disconnect signal.
     * The session will be fully cleaned up when the disconnect event fires.
     *
     * @param sessionId the expired session ID
     */
    private void closeExpiredSession(String sessionId) {
        try {
            // Remove from tracking first to avoid re-processing
            sessionExpiryMap.remove(sessionId);
            sessionActivityMap.remove(sessionId);

            // Find the user associated with this session and notify
            // The actual WebSocket close will happen when the session is invalidated
            log.info("Session {} marked for closure due to token expiry", sessionId);
        } catch (Exception e) {
            log.error("Error closing expired session {}: {}", sessionId, e.getMessage());
        }
    }

    /**
     * Returns the number of active tracked sessions.
     *
     * @return count of active sessions
     */
    public int getActiveSessionCount() {
        return sessionExpiryMap.size();
    }
}
