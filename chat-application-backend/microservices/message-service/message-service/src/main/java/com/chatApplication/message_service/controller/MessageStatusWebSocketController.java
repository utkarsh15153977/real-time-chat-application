package com.chatApplication.message_service.controller;

import com.chatApplication.message_service.dto.BulkStatusUpdateDTO;
import com.chatApplication.message_service.dto.MessageStatusUpdateDTO;
import com.chatApplication.message_service.service.MessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;

import java.security.Principal;

/**
 * STOMP WebSocket controller for message delivery status updates.
 * <p>
 * Handles the SENT -> DELIVERED -> READ state machine transitions
 * and broadcasts status changes to relevant clients.
 * <p>
 * Security model:
 *   - recipientId is ALWAYS extracted from the authenticated Principal
 *   - Never trusts the client-provided recipientId (spoofing protection)
 *   - Principal is set by WebSocketAuthInterceptor during CONNECT frame
 * <p>
 * Endpoints:
 * - /app/chat.markDelivered: Mark a single message as DELIVERED
 * - /app/chat.markRead: Mark a single message as READ
 * - /app/chat.markRoomRead: Bulk mark all unread messages in a room as READ
 * <p>
 * Broadcast destinations:
 * - /topic/room.{chatRoomId}: All subscribers in the chat room
 * - /user/{senderId}/queue/status: Direct status update to message sender
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class MessageStatusWebSocketController {

    private final SimpMessagingTemplate messagingTemplate;
    private final MessageService messageService;

    /**
     * Handles DELIVERED acknowledgment from a recipient's client.
     * <p>
     * Flow:
     *   1. Extract authenticated recipientId from Principal
     *   2. Call service to update status SENT -> DELIVERED
     *   3. Broadcast MessageStatusUpdateDTO to room topic and sender's queue
     *
     * @param request   the status update request (recipientId field is ignored)
     * @param principal the authenticated user
     */
    @MessageMapping("/chat.markDelivered")
    public void markDelivered(StatusUpdateRequest request, Principal principal) {
        String recipientId = extractSenderId(principal);
        if (recipientId == null) {
            log.warn("markDelivered rejected: no authenticated principal");
            return;
        }

        log.debug("markDelivered: message={}, recipient={}",
                request.getMessageId(), recipientId);

        try {
            MessageStatusUpdateDTO update = messageService.markAsDelivered(
                    request.getMessageId(), recipientId);

            if (update != null) {
                // Broadcast to all subscribers in the chat room
                String roomTopic = "/topic/room." + update.getChatRoomId();
                messagingTemplate.convertAndSend(roomTopic, update);

                // Send directly to the message sender's status queue
                messagingTemplate.convertAndSendToUser(
                        update.getSenderId(),
                        "/queue/status",
                        update);

                log.debug("DELIVERED status broadcast: message={}, room={}",
                        update.getMessageId(), update.getChatRoomId());
            }
        } catch (Exception e) {
            log.error("Failed to mark message as delivered", e);
        }
    }

    /**
     * Handles READ acknowledgment from a recipient's client.
     * <p>
     * Flow:
     *   1. Extract authenticated recipientId from Principal
     *   2. Call service to update status SENT/DELIVERED -> READ with readAt timestamp
     *   3. Broadcast MessageStatusUpdateDTO to room topic and sender's queue
     *
     * @param request   the status update request (recipientId field is ignored)
     * @param principal the authenticated user
     */
    @MessageMapping("/chat.markRead")
    public void markRead(StatusUpdateRequest request, Principal principal) {
        String recipientId = extractSenderId(principal);
        if (recipientId == null) {
            log.warn("markRead rejected: no authenticated principal");
            return;
        }

        log.debug("markRead: message={}, recipient={}",
                request.getMessageId(), recipientId);

        try {
            MessageStatusUpdateDTO update = messageService.markAsRead(
                    request.getMessageId(), recipientId);

            if (update != null) {
                // Broadcast to all subscribers in the chat room
                String roomTopic = "/topic/room." + update.getChatRoomId();
                messagingTemplate.convertAndSend(roomTopic, update);

                // Send directly to the message sender's status queue
                messagingTemplate.convertAndSendToUser(
                        update.getSenderId(),
                        "/queue/status",
                        update);

                log.debug("READ status broadcast: message={}, room={}",
                        update.getMessageId(), update.getChatRoomId());
            }
        } catch (Exception e) {
            log.error("Failed to mark message as read", e);
        }
    }

    /**
     * Handles bulk READ acknowledgment when a user opens a conversation.
     * <p>
     * Flow:
     *   1. Extract authenticated readerId from Principal
     *   2. Call service to bulk-update all unread messages in the chat room
     *   3. Broadcast BulkStatusUpdateDTO to room topic
     *
     * @param request   the bulk status update request (readerId field is ignored)
     * @param principal the authenticated user
     */
    @MessageMapping("/chat.markRoomRead")
    public void markRoomRead(BulkStatusUpdateRequest request, Principal principal) {
        String readerId = extractSenderId(principal);
        if (readerId == null) {
            log.warn("markRoomRead rejected: no authenticated principal");
            return;
        }

        log.debug("markRoomRead: room={}, reader={}",
                request.getChatRoomId(), readerId);

        try {
            BulkStatusUpdateDTO bulkUpdate = messageService.markChatRoomAsRead(
                    request.getChatRoomId(), readerId);

            if (bulkUpdate != null && !bulkUpdate.getMessageIds().isEmpty()) {
                // Broadcast to all subscribers in the chat room
                String roomTopic = "/topic/room." + bulkUpdate.getChatRoomId();
                messagingTemplate.convertAndSend(roomTopic, bulkUpdate);

                log.debug("Bulk READ broadcast: room={}, messages={}",
                        bulkUpdate.getChatRoomId(), bulkUpdate.getMessageIds().size());
            }
        } catch (Exception e) {
            log.error("Failed to mark room as read", e);
        }
    }

    /**
     * Extracts the sender ID from the authenticated Principal.
     * The Principal name is set to userId by WebSocketAuthInterceptor.
     *
     * @param principal the STOMP principal
     * @return the authenticated user ID, or null if not authenticated
     */
    private String extractSenderId(Principal principal) {
        if (principal == null) {
            return null;
        }

        if (principal instanceof Authentication auth) {
            return auth.getName();
        }

        return principal.getName();
    }

    /**
     * Inner DTO for single message status update requests.
     * Uses String messageId to match STOMP JSON deserialization.
     */
    @lombok.Data
    public static class StatusUpdateRequest {
        private String messageId;
        private String chatRoomId;
    }

    /**
     * Inner DTO for bulk status update requests.
     */
    @lombok.Data
    public static class BulkStatusUpdateRequest {
        private String chatRoomId;
    }
}
