package com.chatapplication.group_chat.dto.response;

import com.chatapplication.group_chat.entitty.MessageStatus;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageStatusResponse {
    /**
     * Message ID
     */
    private Long messageId;

    /**
     * Conversation ID
     * (Null for group chat)
     */
    private Long conversationId;

    /**
     * Group ID
     * (Null for private chat)
     */
    private Long groupId;

    /**
     * Sender ID
     */
    private Long senderId;

    /**
     * Receiver ID
     * (Null for group chat)
     */
    private Long receiverId;

    /**
     * User who updated the status
     */
    private Long updatedBy;

    /**
     * Current Message Status
     * SENT / DELIVERED / SEEN
     */
    private MessageStatus status;

    /**
     * Delivered Time
     */
    private LocalDateTime deliveredAt;

    /**
     * Seen Time
     */
    private LocalDateTime seenAt;

    /**
     * Event Timestamp
     */
    private LocalDateTime timestamp;
}
