package com.chatApplication.chat_service.config;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
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
 * <p>
 * Security:
 *   - WebSocketAuthInterceptor: Validates JWT on STOMP CONNECT frames
 *   - InternalSecurityFilter: Validates X-Internal-Secret on HTTP upgrade (exempt for /ws)
 *   - /ws endpoint must be accessible from API Gateway for WebSocket upgrade
 */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private static final Logger log =
            LoggerFactory.getLogger(WebSocketConfig.class);

    private final WebSocketAuthInterceptor webSocketAuthInterceptor;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }

    /**
     * Registers the JWT authentication interceptor on the client inbound channel.
     * <p>
     * This interceptor validates JWT tokens on STOMP CONNECT frames
     * before any other STOMP processing occurs.
     *
     * @param registration the channel registration
     */
    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(webSocketAuthInterceptor);
    }
}
