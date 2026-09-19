package com.chatApplication.message_service.service;

import com.chatApplication.message_service.config.InstanceIdentity;
import com.chatApplication.message_service.config.RedisPubSubConfig;
import com.chatApplication.message_service.dto.ChatMessage;
import com.chatApplication.message_service.dto.RedisEnvelope;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Publishes serialized message payloads to the Redis "chat:messages" channel.
 * <p>
 * Used by {@code MessageServiceImpl} after persisting a new message to the
 * database. The published payload is received by all {@code RedisMessageSubscriber}
 * instances, which then relay it to their local WebSocket clients.
 * <p>
 * The payload is wrapped in a {@link RedisEnvelope} containing the originating
 * instance's unique ID. This allows subscribers to skip self-originated messages,
 * preventing duplicate WebSocket delivery on the publishing instance.
 * <p>
 * Fault tolerance: If Redis is temporarily unavailable, the message is still
 * persisted to the database and delivered via the local instance's
 * SimpMessagingTemplate. The Redis publish is a best-effort optimization
 * for cross-instance delivery.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RedisMessagePublisher {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;
    private final InstanceIdentity instanceIdentity;

    /**
     * Wraps the given ChatMessage in a {@link RedisEnvelope} with the current
     * instance's identity and publishes it to the chat messages channel.
     *
     * @param chatMessage the message payload to publish
     */
    public void publish(ChatMessage chatMessage) {
        try {
            RedisEnvelope envelope = RedisEnvelope.builder()
                    .message(chatMessage)
                    .sourceInstanceId(instanceIdentity.getInstanceId())
                    .build();

            String jsonPayload = objectMapper.writeValueAsString(envelope);
            redisTemplate.convertAndSend(
                    RedisPubSubConfig.CHAT_MESSAGES_CHANNEL,
                    jsonPayload);
            log.debug("Published message to Redis channel '{}': instanceId={}",
                    RedisPubSubConfig.CHAT_MESSAGES_CHANNEL,
                    instanceIdentity.getInstanceId());
        } catch (Exception e) {
            // Redis publish failure should not block message sending.
            // The message is already persisted to DB and sent via local
            // SimpMessagingTemplate. Log and continue.
            log.warn("Failed to publish message to Redis channel: {}",
                    e.getMessage());
        }
    }
}
