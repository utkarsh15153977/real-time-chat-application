package com.chatApplication.message_service.service;

import com.chatApplication.message_service.config.InstanceIdentity;
import com.chatApplication.message_service.dto.ChatMessage;
import com.chatApplication.message_service.dto.RedisEnvelope;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

/**
 * Listens for messages published to the Redis "chat:messages" channel
 * and relays them to all WebSocket clients connected to THIS server instance.
 * <p>
 * This is the core of the distributed WebSocket synchronization:
 *   1. Instance A receives a STOMP message from a client
 *   2. Instance A publishes the message payload (wrapped in a
 *      {@link RedisEnvelope} containing the source instance ID)
 *      to Redis channel "chat:messages"
 *   3. ALL instances (including A) receive the message via this listener
 *   4. Each instance checks {@code sourceInstanceId}: if it matches
 *      the current instance, the message is skipped (the publishing
 *      instance already delivered locally). Otherwise, the instance
 *      broadcasts the message to its local WebSocket clients via
 *      SimpMessagingTemplate.
 * <p>
 * This ensures that no matter which server instance a client is connected to,
 * they will receive messages sent by any other client — exactly once per
 * legitimate WebSocket session.
 * <p>
 * Backward compatibility: messages without a {@code sourceInstanceId}
 * (e.g., from older instances during rolling deployment) are treated as
 * remote and delivered normally.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RedisMessageSubscriber implements MessageListener {

    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;
    private final InstanceIdentity instanceIdentity;

    /**
     * Called when a message arrives on the subscribed Redis channel.
     * <p>
     * Expects a {@link RedisEnvelope} JSON payload. If the payload is a
     * bare {@link ChatMessage} (backward compatibility), it is treated as
     * a remote message and delivered normally.
     *
     * @param message raw Redis message (channel + body bytes)
     * @param pattern the pattern used to subscribe (unused here)
     */
    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String payload = new String(message.getBody());
            log.debug("Received message from Redis channel: {}",
                    payload);

            ChatMessage chatMessage;
            String sourceInstanceId = null;

            // Try to deserialize as RedisEnvelope (new format)
            try {
                RedisEnvelope envelope = objectMapper.readValue(
                        payload, RedisEnvelope.class);
                chatMessage = envelope.getMessage();
                sourceInstanceId = envelope.getSourceInstanceId();
            } catch (Exception e) {
                // Backward compatibility: if deserialization as RedisEnvelope
                // fails (e.g., old-format raw ChatMessage), treat as remote
                // and deliver normally
                log.debug("Redis payload is not a RedisEnvelope, "
                        + "treating as legacy format: {}", e.getMessage());
                chatMessage = objectMapper.readValue(
                        payload, ChatMessage.class);
            }

            // Skip self-originated messages to prevent duplicate delivery.
            // The publishing instance already delivered locally via
            // SimpMessagingTemplate.convertAndSendToUser() before publishing.
            if (sourceInstanceId != null
                    && sourceInstanceId.equals(instanceIdentity.getInstanceId())) {
                log.debug("Skipping self-originated Redis message {}",
                        chatMessage.getMsgId());
                return;
            }

            // Forward to the receiver's personal STOMP queue
            // /user/{receiverId}/queue/messages is resolved by
            // SimpMessagingTemplate to the correct user session
            messagingTemplate.convertAndSendToUser(
                    chatMessage.getReceiverId(),
                    "/queue/messages",
                    chatMessage);

            log.debug("Relayed message {} from Redis to WebSocket clients",
                    chatMessage.getMsgId());

        } catch (Exception e) {
            log.error("Failed to process Redis Pub/Sub message", e);
        }
    }
}
