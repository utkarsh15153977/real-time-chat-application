package com.chatApplication.chat_service.service;

import com.chatApplication.chat_service.dto.PresenceEventDTO;
import com.chatApplication.chat_service.dto.TypingEventDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Redis-backed presence service implementation with TTL-based heartbeat.
 * <p>
 * Presence key format: {@code presence:user:{userId}}
 * - Value: "ONLINE"
 * - TTL: 30 seconds (must be refreshed via heartbeat before expiry)
 * <p>
 * Typing key format: {@code typing:{chatRoomId}:{userId}}
 * - Value: "TYPING"
 * - TTL: 3 seconds (auto-expires if user stops typing)
 * <p>
 * Heartbeat strategy:
 * 1. Client sends STOMP frame to /app/heartbeat every N seconds (e.g., 10s)
 * 2. Server calls heartbeat() which refreshes the TTL on the presence key
 * 3. If the client disconnects or crashes, the TTL expires after 30s
 * 4. Expired keys are automatically cleaned up by Redis (no polling needed)
 * <p>
 * This design is resilient to:
 * - Network partitions (TTL handles stale data)
 * - Server instance crashes (Redis survives independently)
 * - Missed disconnect events (TTL acts as a safety net)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RedisPresenceServiceImpl implements PresenceService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final SimpMessagingTemplate messagingTemplate;

    /** Redis key prefix for presence: presence:user:{userId} */
    private static final String PRESENCE_KEY_PREFIX = "presence:user:";

    /** Redis key prefix for typing indicators: typing:{chatRoomId}:{userId} */
    private static final String TYPING_KEY_PREFIX = "typing:";

    /** Presence TTL: 30 seconds. Client must heartbeat before this expires. */
    private static final long PRESENCE_TTL_SECONDS = 30;

    /** Typing indicator TTL: 3 seconds. Auto-expires without explicit stop. */
    private static final long TYPING_TTL_SECONDS = 3;

    @Override
    public void setUserOnline(Long userId) {
        String key = PRESENCE_KEY_PREFIX + userId;

        try {
            // Set key with 30-second TTL
            redisTemplate.opsForValue().set(
                    key, "ONLINE",
                    PRESENCE_TTL_SECONDS, TimeUnit.SECONDS);

            // Broadcast presence event to all connected WebSocket clients
            messagingTemplate.convertAndSend(
                    "/topic/presence",
                    new PresenceEventDTO(userId, "ONLINE"));

            log.info("User {} marked ONLINE in Redis (TTL={}s)",
                    userId, PRESENCE_TTL_SECONDS);

        } catch (Exception e) {
            log.error("Failed to set presence for user {}: {}",
                    userId, e.getMessage());
        }
    }

    @Override
    public void setUserOffline(Long userId) {
        String key = PRESENCE_KEY_PREFIX + userId;

        try {
            // Delete the presence key
            redisTemplate.delete(key);

            // Broadcast offline event
            messagingTemplate.convertAndSend(
                    "/topic/presence",
                    new PresenceEventDTO(userId, "OFFLINE"));

            log.info("User {} marked OFFLINE in Redis", userId);

        } catch (Exception e) {
            log.error("Failed to clear presence for user {}: {}",
                    userId, e.getMessage());
        }
    }

    @Override
    public void heartbeat(Long userId) {
        String key = PRESENCE_KEY_PREFIX + userId;

        try {
            // Only refresh TTL if the user is currently online
            // This prevents a heartbeat from resurrecting a stale user
            Boolean exists = redisTemplate.hasKey(key);
            if (Boolean.TRUE.equals(exists)) {
                redisTemplate.expire(
                        key, PRESENCE_TTL_SECONDS, TimeUnit.SECONDS);
                log.debug("Heartbeat refreshed for user {}", userId);
            } else {
                // User's presence expired; treat as reconnection
                setUserOnline(userId);
                log.info("Heartbeat triggered reconnection for user {}", userId);
            }

        } catch (Exception e) {
            log.error("Failed to process heartbeat for user {}: {}",
                    userId, e.getMessage());
        }
    }

    @Override
    public void setTyping(Long chatRoomId, Long userId, boolean isTyping) {
        String key = TYPING_KEY_PREFIX + chatRoomId + ":" + userId;

        try {
            if (isTyping) {
                // Set typing key with 3-second TTL (auto-expires)
                redisTemplate.opsForValue().set(
                        key, "TYPING",
                        TYPING_TTL_SECONDS, TimeUnit.SECONDS);
            } else {
                // Explicitly stop typing - remove key immediately
                redisTemplate.delete(key);
            }

            // Broadcast typing event to chat room subscribers
            // Transient: NOT persisted to database
            TypingEventDTO event = new TypingEventDTO(
                    chatRoomId, userId, isTyping);

            messagingTemplate.convertAndSend(
                    "/topic/chat." + chatRoomId + ".typing",
                    event);

            log.debug("Typing indicator for user {} in room {}: {}",
                    userId, chatRoomId, isTyping);

        } catch (Exception e) {
            log.error("Failed to set typing indicator for user {} in room {}: {}",
                    userId, chatRoomId, e.getMessage());
        }
    }

    @Override
    public boolean isUserOnline(Long userId) {
        String key = PRESENCE_KEY_PREFIX + userId;
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(key));
        } catch (Exception e) {
            log.error("Failed to check presence for user {}: {}",
                    userId, e.getMessage());
            return false;
        }
    }

    @Override
    public Map<Long, Boolean> getBulkPresenceStatus(List<Long> userIds) {
        Map<Long, Boolean> statuses = new LinkedHashMap<>();

        try {
            for (Long userId : userIds) {
                statuses.put(userId, isUserOnline(userId));
            }
        } catch (Exception e) {
            log.error("Failed to fetch bulk presence status: {}",
                    e.getMessage());
            // Return all as offline on error
            userIds.forEach(id -> statuses.put(id, false));
        }

        return statuses;
    }
}
