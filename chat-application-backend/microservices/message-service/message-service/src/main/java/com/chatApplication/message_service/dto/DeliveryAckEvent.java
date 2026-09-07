package com.chatApplication.message_service.dto;

import com.chatApplication.message_service.entity.MessageStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO for delivery acknowledgment events sent over WebSocket.
 * <p>
 * Used for both DELIVERED_ACK and READ_ACK events:
 * - DELIVERED_ACK: Recipient's client received the message
 * - READ_ACK: Recipient's client opened the chat and viewed the message
 * <p>
 * Sent to the sender on /queue/receipts so they can update their
 * UI with the appropriate message status indicator (single check, double check, etc.)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryAckEvent {
    /** The message ID that was acknowledged */
    private Long messageId;

    /** The chat room the message belongs to */
    private Long chatRoomId;

    /** The user ID of the sender (for targeting the receipt) */
    private String senderId;

    /** The user ID of the recipient who sent the ACK */
    private String recipientId;

    /** The new status (DELIVERED or READ) */
    private MessageStatus status;

    /** Timestamp of when the ACK was processed */
    private LocalDateTime timestamp;
}
