package com.chatApplication.chat_service.service;

import java.util.Map;

/**
 * Service interface for managing user online/offline presence and typing indicators.
 * <p>
 * Presence is tracked in Redis with a TTL-based heartbeat mechanism:
 * - When a user connects or sends a heartbeat, their presence key is set/refreshed
 * - If a user disconnects or their heartbeat expires, they are marked offline
 * - The 30-second TTL ensures that stale presence data is automatically cleaned up
 *   even if the disconnect event is missed (e.g., network failure)
 */
public interface PresenceService {

    /**
     * Mark a user as ONLINE by creating/refreshing a Redis key with TTL.
     * Also publishes a presence event to /topic/presence.
     *
     * @param userId the user's ID
     */
    void setUserOnline(Long userId);

    /**
     * Mark a user as OFFLINE by deleting their Redis presence key.
     * Also publishes a presence event to /topic/presence.
     *
     * @param userId the user's ID
     */
    void setUserOffline(Long userId);

    /**
     * Refresh the TTL on an existing presence key.
     * Only refreshes if the user is currently online.
     * This is called periodically by the client sending heartbeats.
     *
     * @param userId the user's ID
     */
    void heartbeat(Long userId);

    /**
     * Set or clear a transient typing indicator for a user in a chat room.
     * Typing state is stored in Redis with a short TTL (3 seconds) and
     * published to /topic/chat.{chatRoomId}.typing for real-time display.
     *
     * @param chatRoomId the chat room ID
     * @param userId     the typing user's ID
     * @param isTyping   true if the user started typing, false if they stopped
     */
    void setTyping(Long chatRoomId, Long userId, boolean isTyping);

    /**
     * Check whether a user is currently online.
     *
     * @param userId the user's ID
     * @return true if the user has an active presence key in Redis
     */
    boolean isUserOnline(Long userId);

    /**
     * Get online status for multiple users in a single Redis operation.
     *
     * @param userIds list of user IDs to check
     * @return map of userId -> isOnline status
     */
    Map<Long, Boolean> getBulkPresenceStatus(java.util.List<Long> userIds);
}
