package com.chatApplication.message_service.kafka;

import com.chatApplication.message_service.entity.MessageType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Kafka event published when a new chat message is persisted.
 * <p>
 * Downstream consumers (notification-service, search-service, analytics-service)
 * subscribe to the {@code chat.message-created} topic to process messages
 * asynchronously without coupling to the message-service database.
 * <p>
 * Partitioning strategy: Events are keyed by {@code chatRoomId} to guarantee
 * in-order delivery per conversation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageCreatedEvent {

    /** Unique message identifier (string representation of the DB primary key). */
    private String messageId;

    /** Chat room identifier (format: "{userId1}-{userId2}", lexicographically sorted). */
    private String chatRoomId;

    /** Sender user ID. */
    private String senderId;

    /** Recipient user ID. */
    private String recipientId;

    /** Message content (text body or media caption). */
    private String content;

    /** Message type (TEXT, IMAGE, VIDEO, AUDIO, FILE). */
    private MessageType type;

    /** ISO-8601 timestamp of when the message was persisted. */
    private Instant createdAt;
}
