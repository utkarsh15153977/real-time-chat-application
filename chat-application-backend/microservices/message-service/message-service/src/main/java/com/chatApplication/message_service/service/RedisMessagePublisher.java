package com.chatApplication.message_service.service;

import com.chatApplication.message_service.config.RedisPubSubConfig;
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

    /**
     * Publishes a serializable payload to the chat messages channel.
     *
     * @param payload the object to serialize and publish (typically a ChatMessage DTO)
     */
    public void publish(Object payload) {
        try {
            String jsonPayload = objectMapper.writeValueAsString(payload);
            redisTemplate.convertAndSend(
                    RedisPubSubConfig.CHAT_MESSAGES_CHANNEL,
                    jsonPayload);
            log.debug("Published message to Redis channel '{}': {}",
                    RedisPubSubConfig.CHAT_MESSAGES_CHANNEL, jsonPayload);
        } catch (Exception e) {
            // Redis publish failure should not block message sending.
            // The message is already persisted to DB and sent via local
            // SimpMessagingTemplate. Log and continue.
            log.warn("Failed to publish message to Redis channel: {}",
                    e.getMessage());
        }
    }
}
