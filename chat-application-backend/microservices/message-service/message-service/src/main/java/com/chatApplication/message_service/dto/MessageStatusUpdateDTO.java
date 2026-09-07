package com.chatApplication.message_service.dto;

import com.chatApplication.message_service.entity.MessageStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * DTO for broadcasting single message status transitions via STOMP WebSocket.
 * <p>
 * Sent to /topic/room.{chatRoomId} and /user/{senderId}/queue/status
 * when a message's status changes (SENT -> DELIVERED -> READ).
 * <p>
 * Clients use this to update the message status indicator in the UI
 * (single check for SENT, double check for DELIVERED, filled double check for READ).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageStatusUpdateDTO {

    /** The message ID whose status changed */
    private String messageId;

    /** The chat room the message belongs to */
    private String chatRoomId;

    /** The user ID of the message sender (for targeting receipt updates) */
    private String senderId;

    /** The user ID of the recipient who triggered the status change */
    private String recipientId;

    /** The new status: SENT, DELIVERED, or READ */
    private MessageStatus status;

    /** Timestamp when the status change occurred */
    private Instant timestamp;
}
