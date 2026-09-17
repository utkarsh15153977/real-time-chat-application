package com.chatApplication.message_service.config;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

/**
 * WebSocket configuration for the message-service.
 * <p>
 * Uses STOMP over SockJS for broad browser compatibility.
 * The simple in-memory broker handles local message routing.
 * Redis Pub/Sub (via {@code RedisPubSubConfig}) handles cross-instance delivery.
 * <p>
 * Security:
 *   - WebSocketAuthInterceptor: Validates JWT on CONNECT frames
 *   - InternalSecurityFilter: Validates X-Internal-Secret on HTTP upgrade
 *   - WebSocketSessionExpiryManager: Tracks and expires stale sessions
 *   - /ws endpoint must be accessible from API Gateway for WebSocket upgrade
 * <p>
 * Broker prefixes:
 * - /topic: broadcast topics (presence events, typing indicators)
 * - /queue: user-specific queues (direct messages, delivery receipts)
 * - /user: Spring's user destination prefix for SimpMessagingTemplate.convertAndSendToUser()
 * <p>
 * KAN-3 fix:
 *   - SubscriptionReadinessInterceptor: Buffers SEND frames while subscription
 *     registration is pending, preventing the SUBSCRIBE → SEND ordering race.
 */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private static final Logger log =
            LoggerFactory.getLogger(WebSocketConfig.class);

    private final WebSocketAuthInterceptor webSocketAuthInterceptor;
    private final SubscriptionReadinessInterceptor subscriptionReadinessInterceptor;

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

        // Native WebSocket endpoint - use this for k6
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*");

        // SockJS endpoint - keep this for browser/client compatibility
        registry.addEndpoint("/ws-sockjs")
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }

    /**
     * Registers interceptors on the client inbound channel.
     * <p>
     * Interceptors execute in order:
     *   1. WebSocketAuthInterceptor (validates JWT on STOMP CONNECT)
     *   2. SubscriptionReadinessInterceptor (buffers SEND while SUBSCRIBE is pending)
     * <p>
     * The SubscriptionReadinessInterceptor implements ExecutorChannelInterceptor
     * and must be registered here so that its {@code afterMessageHandled()} callback
     * fires after SimpleBrokerMessageHandler completes subscription registration.
     *
     * @param registration the channel registration
     */
    @Override
    public void configureClientInboundChannel(
            ChannelRegistration registration) {
        registration.interceptors(webSocketAuthInterceptor);
        registration.interceptors(subscriptionReadinessInterceptor);
    }

    /**
     * Cleans up SubscriptionReadinessInterceptor state when a WebSocket
     * session disconnects. Prevents state leakage from pending subscriptions
     * and buffered SENDs for disconnected sessions.
     *
     * @param event the session disconnect event
     */
    @org.springframework.context.event.EventListener
    public void handleDisconnect(SessionDisconnectEvent event) {
        String sessionId = event.getSessionId();
        subscriptionReadinessInterceptor.removeSession(sessionId);
        log.debug("SubscriptionReadinessInterceptor cleaned up for session={}", sessionId);
    }
}
