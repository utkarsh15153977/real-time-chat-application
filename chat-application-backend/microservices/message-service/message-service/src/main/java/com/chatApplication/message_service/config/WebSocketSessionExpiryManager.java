package com.chatApplication.message_service.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages WebSocket session expiration by tracking JWT token expiry
 * and cleaning up expired sessions.
 *
 * <p>Security model:</p>
 * <ul>
 *     <li>During CONNECT, WebSocketAuthInterceptor stores the JWT
 *         expiration timestamp.</li>
 *     <li>This manager tracks the expiration time for each STOMP session.</li>
 *     <li>A scheduled task periodically detects expired sessions.</li>
 *     <li>Inbound frames can also be checked against the tracked expiry.</li>
 * </ul>
 *
 * <p>This component intentionally does NOT depend on
 * SimpMessagingTemplate or SimpUserRegistry.</p>
 *
 * <p>This is important because those WebSocket infrastructure beans are
 * created by Spring's WebSocket message broker configuration. Depending
 * on them from the authentication interceptor would create a circular
 * dependency during application startup.</p>
 */
@Slf4j
@Component
@EnableScheduling
public class WebSocketSessionExpiryManager {

    /**
     * Tracks token expiry timestamps:
     *
     * sessionId -> token expiry
     */
    private final Map<String, Instant> sessionExpiryMap =
            new ConcurrentHashMap<>();

    /**
     * Tracks last activity timestamps:
     *
     * sessionId -> last activity
     */
    private final Map<String, Instant> sessionActivityMap =
            new ConcurrentHashMap<>();

    /**
     * Registers a WebSocket session with its token expiry time.
     *
     * @param sessionId the STOMP session ID
     * @param tokenExp  the JWT expiration time
     */
    public void registerSession(
            String sessionId,
            Instant tokenExp) {

        if (sessionId == null || sessionId.isBlank()) {
            log.warn("Cannot register WebSocket session: sessionId is null/blank");
            return;
        }

        if (tokenExp == null) {
            log.warn(
                    "Cannot register WebSocket session {}: token expiry is null",
                    sessionId);
            return;
        }

        sessionExpiryMap.put(sessionId, tokenExp);
        sessionActivityMap.put(sessionId, Instant.now());

        log.debug(
                "Registered WebSocket session {} with expiry {}",
                sessionId,
                tokenExp);
    }

    /**
     * Updates the last activity timestamp for a session.
     *
     * @param sessionId the STOMP session ID
     */
    public void updateActivity(String sessionId) {

        if (sessionId == null || sessionId.isBlank()) {
            return;
        }

        if (sessionExpiryMap.containsKey(sessionId)) {
            sessionActivityMap.put(sessionId, Instant.now());
        }
    }

    /**
     * Removes a session from the expiration tracking maps.
     *
     * @param sessionId the STOMP session ID
     */
    public void removeSession(String sessionId) {

        if (sessionId == null || sessionId.isBlank()) {
            return;
        }

        sessionExpiryMap.remove(sessionId);
        sessionActivityMap.remove(sessionId);

        log.debug(
                "Removed WebSocket session {} from expiry tracking",
                sessionId);
    }

    /**
     * Checks whether a session's JWT has expired.
     *
     * @param sessionId the STOMP session ID
     * @return true if the session is expired or unknown
     */
    public boolean isSessionExpired(String sessionId) {

        if (sessionId == null || sessionId.isBlank()) {
            return true;
        }

        Instant expiry = sessionExpiryMap.get(sessionId);

        if (expiry == null) {
            // Unknown session is considered expired for security.
            return true;
        }

        return Instant.now().isAfter(expiry);
    }

    /**
     * Determines whether an inbound frame can be processed.
     *
     * @param sessionId the STOMP session ID
     * @return true if the session is still valid
     */
    public boolean canProcessFrame(String sessionId) {
        return !isSessionExpired(sessionId);
    }

    /**
     * Periodically checks all tracked sessions and removes
     * sessions whose JWT has expired.
     *
     * Default interval: 30 seconds.
     */
    @Scheduled(
            fixedDelayString =
                    "${security.websocket.expiry-check-interval:30000}"
    )
    public void checkExpiredSessions() {

        if (sessionExpiryMap.isEmpty()) {
            return;
        }

        Instant now = Instant.now();

        int expiredCount = 0;

        for (Map.Entry<String, Instant> entry
                : sessionExpiryMap.entrySet()) {

            String sessionId = entry.getKey();
            Instant expiry = entry.getValue();

            if (expiry != null && now.isAfter(expiry)) {

                log.info(
                        "WebSocket session {} has expired at {}",
                        sessionId,
                        expiry);

                removeSession(sessionId);

                expiredCount++;
            }
        }

        if (expiredCount > 0) {
            log.info(
                    "Removed {} expired WebSocket sessions",
                    expiredCount);
        }
    }

    /**
     * Returns the number of currently tracked sessions.
     *
     * @return number of tracked sessions
     */
    public int getActiveSessionCount() {
        return sessionExpiryMap.size();
    }
}