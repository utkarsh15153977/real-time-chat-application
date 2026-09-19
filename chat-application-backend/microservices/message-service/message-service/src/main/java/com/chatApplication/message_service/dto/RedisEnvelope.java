package com.chatApplication.message_service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Redis Pub/Sub transport envelope for cross-instance WebSocket synchronization.
 * <p>
 * Wraps a {@link ChatMessage} payload with a {@code sourceInstanceId} so that
 * the publishing instance can identify and skip its own messages when they
 * arrive back via the Redis subscriber. This prevents duplicate WebSocket
 * delivery to locally connected clients.
 * <p>
 * <b>Internal use only.</b> This DTO is serialized/deserialized exclusively
 * for the Redis "chat:messages" channel. It is never sent to WebSocket
 * clients — clients receive the inner {@link ChatMessage} payload only.
 * <p>
 * Backward compatibility: if {@code sourceInstanceId} is null (e.g., from an
 * older instance that does not yet include it), the subscriber treats the
 * message as remote and delivers it normally. This supports rolling deployments.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RedisEnvelope {

    /** The chat message payload to deliver via WebSocket */
    private ChatMessage message;

    /** UUID of the application instance that published this message */
    private String sourceInstanceId;
}
