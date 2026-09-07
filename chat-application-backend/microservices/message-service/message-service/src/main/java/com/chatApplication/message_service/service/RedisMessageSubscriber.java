package com.chatApplication.message_service.service;

import com.chatApplication.message_service.dto.ChatMessage;
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
 *   2. Instance A publishes the message payload to Redis channel "chat:messages"
 *   3. ALL instances (including A) receive the message via this listener
 *   4. Each instance broadcasts it to its local WebSocket clients via SimpMessagingTemplate
 * <p>
 * This ensures that no matter which server instance a client is connected to,
 * they will receive messages sent by any other client.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RedisMessageSubscriber implements MessageListener {

    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    /**
     * Called when a message arrives on the subscribed Redis channel.
     * Deserializes the JSON payload into a ChatMessage and forwards
     * it to the appropriate STOMP user queue.
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

            // Deserialize the JSON payload into a ChatMessage DTO
            ChatMessage chatMessage = objectMapper.readValue(
                    payload, ChatMessage.class);

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
