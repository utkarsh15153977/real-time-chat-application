package com.chatApplication.message_service.controller;

import com.chatApplication.message_service.service.DeliveryAckHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.Map;

/**
 * WebSocket controller for delivery acknowledgment (ACK) handlers.
 * <p>
 * Clients send ACK frames to confirm message delivery and read status:
 * - /app/chat.deliveredAck: Recipient received the message
 * - /app/chat.readAck: Recipient opened the chat and viewed messages
 * <p>
 * The controller validates the principal and delegates to
 * {@link DeliveryAckHandler} for business logic processing.
 * <p>
 * ACK flow:
 *   Client sends ACK -> Controller validates -> Handler updates DB
 *   -> Handler sends receipt event to sender via /queue/receipts
 *   -> Sender's UI updates message status indicator
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class DeliveryAckWebSocketController {

    private final DeliveryAckHandler deliveryAckHandler;

    /**
     * Handles DELIVERED_ACK from the recipient's client.
     * <p>
     * Expected payload: {"messageId": 123}
     * Client sends to: /app/chat.deliveredAck
     * <p>
     * The principal's name is the recipient's user ID (set during
     * WebSocket authentication in the STOMP CONNECT frame).
     *
     * @param payload   JSON with messageId
     * @param principal authenticated user (the recipient)
     */
    @MessageMapping("/chat.deliveredAck")
    public void handleDeliveredAck(
            @Payload Map<String, Object> payload,
            Principal principal) {

        if (principal == null) {
            log.warn("DELIVERED_ACK rejected: no principal");
            return;
        }

        String recipientId = principal.getName();
        Long messageId = extractLong(payload, "messageId");

        if (messageId == null) {
            log.warn("DELIVERED_ACK rejected: invalid messageId from user {}",
                    recipientId);
            return;
        }

        deliveryAckHandler.handleDeliveredAck(messageId, recipientId);
    }

    /**
     * Handles READ_ACK from the recipient's client.
     * <p>
     * Expected payload: {"senderId": "user-456"}
     * Client sends to: /app/chat.readAck
     * <p>
     * The senderId in the payload is the OTHER user in the conversation
     * (the one who sent the messages that need to be marked as read).
     * The principal is the recipient who is viewing the conversation.
     *
     * @param payload   JSON with senderId (the other party's user ID)
     * @param principal authenticated user (the recipient viewing the chat)
     */
    @MessageMapping("/chat.readAck")
    public void handleReadAck(
            @Payload Map<String, Object> payload,
            Principal principal) {

        if (principal == null) {
            log.warn("READ_ACK rejected: no principal");
            return;
        }

        String recipientId = principal.getName();
        String senderId = (String) payload.get("senderId");

        if (senderId == null || senderId.isBlank()) {
            log.warn("READ_ACK rejected: invalid senderId from user {}",
                    recipientId);
            return;
        }

        deliveryAckHandler.handleReadAck(senderId, recipientId);
    }

    /**
     * Safely extracts a Long value from the payload map.
     * Handles both Integer and Long types from JSON deserialization.
     *
     * @param payload the payload map
     * @param key     the key to extract
     * @return parsed Long value, or null if missing/invalid
     */
    private Long extractLong(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            log.warn("Invalid {} value: {}", key, value);
            return null;
        }
    }
}
