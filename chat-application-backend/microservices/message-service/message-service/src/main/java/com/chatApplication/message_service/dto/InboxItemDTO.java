package com.chatApplication.message_service.dto;

import com.chatApplication.message_service.entity.MessageType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * DTO representing a single conversation in the user's inbox.
 * <p>
 * Contains the last message preview, recipient details, and unread count
 * for efficient inbox rendering without N+1 query overhead.
 * <p>
 * The chatRoomId follows the format "{userId1}-{userId2}" where userId1 < userId2
 * (lexicographically sorted) to ensure consistent room identification.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InboxItemDTO {

    /** Unique chat room identifier (format: "{userId1}-{userId2}") */
    private String chatRoomId;

    /** User ID of the conversation partner */
    private String recipientId;

    /** Content of the most recent message in the conversation */
    private String lastMessageContent;

    /** Type of the last message (TEXT, IMAGE, VIDEO, AUDIO, FILE) */
    private MessageType lastMessageType;

    /** Timestamp of the most recent message (epoch seconds) */
    private Instant lastMessageTimestamp;

    /** User ID of the last message sender */
    private String lastMessageSenderId;

    /** Number of unread messages in this conversation for the current user */
    private long unreadCount;
}
