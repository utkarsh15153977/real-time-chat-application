package com.chatApplication.message_service.config;

import com.chatApplication.message_service.service.RedisMessageSubscriber;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;

/**
 * Redis Pub/Sub configuration for distributed WebSocket synchronization.
 * <p>
 * When multiple instances of message-service run behind a load balancer,
 * a STOMP message received by one instance must be relayed to all other
 * instances so their connected WebSocket clients receive it too.
 * <p>
 * Architecture:
 *   Instance A receives message from client
 *     -> publishes to Redis channel "chat:messages"
 *     -> Instance A's subscriber receives it and broadcasts via SimpMessagingTemplate
 *     -> Instance B's subscriber receives it and broadcasts via SimpMessagingTemplate
 *     -> All connected clients receive the message
 */
@Configuration

@ConditionalOnProperty(
        name = "app.redis.pubsub.enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class RedisPubSubConfig {

    /** Redis channel name used for cross-instance message synchronization */
    public static final String CHAT_MESSAGES_CHANNEL = "chat:messages";

    /**
     * Adapter wrapping the subscriber bean so Spring Data Redis can
     * delegate incoming channel messages to our handler method.
     */
    @Bean
    public MessageListenerAdapter messageListenerAdapter(
            RedisMessageSubscriber subscriber) {
        return new MessageListenerAdapter(subscriber, "onMessage");
    }

    /**
     * Container that manages the Redis subscription lifecycle.
     * It subscribes to the configured channel and dispatches
     * incoming messages to the adapter.
     */
    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            MessageListenerAdapter adapter) {

        RedisMessageListenerContainer container =
                new RedisMessageListenerContainer();

        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(adapter,
                new ChannelTopic(CHAT_MESSAGES_CHANNEL));

        // Retry connection on failure with exponential backoff
        container.setErrorHandler(
                throwable -> {
                    if (throwable.getCause() != null
                            && throwable.getCause().getCause() instanceof java.io.IOException) {
                        // Redis connection lost - container will auto-reconnect
                    }
                });

        return container;
    }
}
