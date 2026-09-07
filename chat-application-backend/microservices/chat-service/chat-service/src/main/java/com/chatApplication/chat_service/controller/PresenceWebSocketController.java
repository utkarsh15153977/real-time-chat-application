package com.chatApplication.chat_service.controller;

import com.chatApplication.chat_service.service.PresenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.Map;

/**
 * WebSocket controller for handling transient user interactions.
 * <p>
 * Typing indicators are lightweight events that:
 * 1. Are stored in Redis with a 3-second TTL (auto-expire)
 * 2. Are broadcast to /topic/chat.{chatRoomId}.typing
 * 3. Do NOT hit the database
 * 4. Are NOT persisted anywhere
 * <p>
 * Heartbeat handler keeps user presence alive by refreshing the Redis TTL.
 * Client should send heartbeat every ~10 seconds to maintain the 30s TTL.
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class PresenceWebSocketController {

    private final PresenceService presenceService;

    /**
     * Handles typing indicator events.
     * <p>
     * Client sends to: /app/chat.{chatRoomId}.typing
     * Broadcast to: /topic/chat.{chatRoomId}.typing
     *
     * @param chatRoomId  the chat room ID (from destination path)
     * @param payload     JSON with "isTyping" boolean
     * @param principal   authenticated user (injected by STOMP interceptor)
     */
    @MessageMapping("/chat.{chatRoomId}.typing")
    public void handleTyping(
            @DestinationVariable Long chatRoomId,
            @Payload Map<String, Object> payload,
            Principal principal) {

        if (principal == null) {
            log.warn("Typing event rejected: no principal");
            return;
        }

        // Extract userId from principal (set during WebSocket auth)
        Long userId = extractUserId(principal);
        if (userId == null) {
            return;
        }

        Boolean isTyping = (Boolean) payload.getOrDefault("isTyping", false);

        // Store transient typing state in Redis and broadcast
        presenceService.setTyping(chatRoomId, userId, isTyping);
    }

    /**
     * Handles client heartbeat to keep presence alive.
     * <p>
     * Client sends to: /app/heartbeat
     * Server refreshes the Redis TTL on the user's presence key.
     *
     * @param principal authenticated user
     */
    @MessageMapping("/heartbeat")
    public void handleHeartbeat(Principal principal) {
        if (principal == null) {
            return;
        }

        Long userId = extractUserId(principal);
        if (userId != null) {
            presenceService.heartbeat(userId);
        }
    }

    /**
     * Extracts the user ID from the Principal object.
     * The principal name is expected to be the user ID string.
     *
     * @param principal the authenticated STOMP principal
     * @return parsed user ID, or null if invalid
     */
    private Long extractUserId(Principal principal) {
        try {
            return Long.parseLong(principal.getName());
        } catch (NumberFormatException e) {
            log.warn("Invalid userId in principal: {}",
                    principal.getName());
            return null;
        }
    }
}
