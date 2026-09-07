package com.chatApplication.message_service.dto;

import com.chatApplication.message_service.entity.MessageStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * DTO for broadcasting bulk message status updates via STOMP WebSocket.
 * <p>
 * Sent to /topic/room.{chatRoomId} when a user opens a conversation
 * and all unread messages are marked as read in bulk.
 * <p>
 * Clients use this to update multiple message status indicators at once
 * when the backend confirms a bulk read operation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkStatusUpdateDTO {

    /** The chat room ID where the bulk update occurred */
    private String chatRoomId;

    /** The user ID of the reader who triggered the bulk update */
    private String readerId;

    /** List of message IDs that were updated */
    private List<String> messageIds;

    /** The status that was applied (typically READ) */
    private MessageStatus status;

    /** Timestamp when the bulk update occurred */
    private Instant timestamp;
}
