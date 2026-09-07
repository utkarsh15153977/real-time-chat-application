package com.chatApplication.message_service.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Service for checking real-time user presence status.
 * <p>
 * Delegates to {@link PresenceTracker} which maintains a Redis SET
 * of currently connected user IDs. A user is considered "connected"
 * if they have an active WebSocket STOMP session and have broadcast
 * a presence.online event.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserPresenceService {

    private final PresenceTracker presenceTracker;

    /**
     * Checks whether the given user has an active WebSocket connection.
     *
     * @param userId the user ID to check
     * @return {@code true} if the user is currently connected via WebSocket
     */
    public boolean isUserConnected(String userId) {
        boolean online = presenceTracker.isOnline(userId);
        log.debug("Presence check for user {}: {}", userId, online ? "ONLINE" : "OFFLINE");
        return online;
    }
}
