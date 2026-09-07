package com.chatApplication.chat_service.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * WebSocket configuration for the chat-service.
 * <p>
 * Provides STOMP over SockJS for:
 * - Presence events: /topic/presence (broadcast online/offline status)
 * - Typing indicators: /topic/chat.{roomId}.typing (transient typing state)
 * - Heartbeat: /app/heartbeat (client-initiated keep-alive)
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(
            MessageBrokerRegistry registry) {

        // Simple in-memory broker for presence and typing topics
        registry.enableSimpleBroker("/topic");

        // Client sends to /app/heartbeat, /app/chat.typing, etc.
        registry.setApplicationDestinationPrefixes("/app");

        // User-specific destinations for targeted presence queries
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(
            StompEndpointRegistry registry) {

        // SockJS WebSocket handshake endpoint
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }
}
