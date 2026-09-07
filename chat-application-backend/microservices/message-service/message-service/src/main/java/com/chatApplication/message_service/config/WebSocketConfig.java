package com.chatApplication.message_service.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * WebSocket configuration for the message-service.
 * <p>
 * Uses STOMP over SockJS for broad browser compatibility.
 * The simple in-memory broker handles local message routing.
 * Redis Pub/Sub (via {@code RedisPubSubConfig}) handles cross-instance delivery.
 * <p>
 * Broker prefixes:
 * - /topic: broadcast topics (presence events, typing indicators)
 * - /queue: user-specific queues (direct messages, delivery receipts)
 * - /user: Spring's user destination prefix for SimpMessagingTemplate.convertAndSendToUser()
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(
            MessageBrokerRegistry registry) {

        // Enable the built-in simple broker for /topic and /queue destinations.
        // In production with multiple instances, this is supplemented by
        // Redis Pub/Sub for cross-instance message propagation.
        registry.enableSimpleBroker(
                "/topic",
                "/queue");

        // Application destination prefix: client STOMP frames sent to /app/xxx
        // are handled by @MessageMapping methods
        registry.setApplicationDestinationPrefixes(
                "/app");

        // User destination prefix: SimpMessagingTemplate.convertAndSendToUser()
        // resolves /user/{sessionId}/queue/xxx to the correct session
        registry.setUserDestinationPrefix(
                "/user");
    }

    @Override
    public void registerStompEndpoints(
            StompEndpointRegistry registry) {

        // SockJS fallback endpoint for WebSocket connections
        // Allows connections from any origin (API Gateway handles auth)
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }
}
