package com.chatApplication.chat_service.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks JWT token expiry for WebSocket STOMP sessions.
 * <p>
 * A scheduled task periodically detects and removes expired sessions.
 */
@Slf4j
@Component
@EnableScheduling
public class WebSocketSessionExpiryManager {

    private final Map<String, Instant> sessionExpiryMap =
            new ConcurrentHashMap<>();

    public void registerSession(String sessionId, Instant tokenExp) {
        if (sessionId == null || sessionId.isBlank() || tokenExp == null) {
            return;
        }
        sessionExpiryMap.put(sessionId, tokenExp);
        log.debug("Registered WebSocket session {} with expiry {}", sessionId, tokenExp);
    }

    public void removeSession(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        sessionExpiryMap.remove(sessionId);
        log.debug("Removed WebSocket session {} from expiry tracking", sessionId);
    }

    public boolean isSessionExpired(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return true;
        }
        Instant expiry = sessionExpiryMap.get(sessionId);
        if (expiry == null) {
            return true;
        }
        return Instant.now().isAfter(expiry);
    }

    public boolean canProcessFrame(String sessionId) {
        return !isSessionExpired(sessionId);
    }

    @Scheduled(fixedDelayString = "${security.websocket.expiry-check-interval:30000}")
    public void checkExpiredSessions() {
        if (sessionExpiryMap.isEmpty()) {
            return;
        }
        Instant now = Instant.now();
        int expiredCount = 0;
        for (Map.Entry<String, Instant> entry : sessionExpiryMap.entrySet()) {
            Instant expiry = entry.getValue();
            if (expiry != null && now.isAfter(expiry)) {
                log.info("WebSocket session {} has expired", entry.getKey());
                removeSession(entry.getKey());
                expiredCount++;
            }
        }
        if (expiredCount > 0) {
            log.info("Removed {} expired WebSocket sessions", expiredCount);
        }
    }

    public int getActiveSessionCount() {
        return sessionExpiryMap.size();
    }
}
