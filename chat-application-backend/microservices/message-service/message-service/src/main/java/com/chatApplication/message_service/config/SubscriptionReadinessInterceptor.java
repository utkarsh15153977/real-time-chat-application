package com.chatApplication.message_service.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.broker.SimpleBrokerMessageHandler;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ExecutorChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Server-side subscription readiness interceptor that resolves the STOMP
 * SUBSCRIBE → SEND ordering race (KAN-3).
 *
 * <p>Problem: When a client sends SUBSCRIBE and SEND in quick succession,
 * both frames are dispatched concurrently on the {@code clientInboundChannel}
 * executor. The SEND's {@code convertAndSend()} can reach
 * {@code findSubscriptions()} before the SUBSCRIBE's
 * {@code registerSubscription()} completes, causing the first broadcast
 * message to be lost.
 *
 * <p>Solution: This interceptor buffers SEND frames while a subscription
 * registration is pending for the same session. When
 * {@link SimpleBrokerMessageHandler} completes registration and
 * {@link #afterMessageHandled} fires, buffered SENDs are released.
 *
 * <p>Scoping: Readiness is tracked per {@code (sessionId)} to ensure that
 * one client's subscription readiness cannot incorrectly release another
 * client's buffered SEND.
 *
 * <p>Threading: No blocking synchronization is used. All data structures
 * are lock-free ({@link ConcurrentHashMap}, {@link CopyOnWriteArrayList}).
 * Buffered SENDs are re-dispatched asynchronously via
 * {@code clientInboundChannel.send()}.
 *
 * <p>This class is intentionally placed in the config package alongside
 * {@link WebSocketAuthInterceptor} and is registered on the
 * {@code clientInboundChannel} through {@link WebSocketConfig}.
 *
 * <p>PERMANENT FIX for KAN-3. Remove only if the underlying Spring
 * Framework provides an equivalent mechanism.
 */
@Component
public class SubscriptionReadinessInterceptor implements ExecutorChannelInterceptor {

    private static final Logger log =
            LoggerFactory.getLogger(SubscriptionReadinessInterceptor.class);

    /**
     * Per-session set of destinations with pending subscription registrations.
     * Key: STOMP session ID. Value: set of destinations being subscribed to.
     *
     * <p>Populated in {@link #preSend} when a SUBSCRIBE frame arrives.
     * Cleaned up in {@link #afterMessageHandled} when
     * {@link SimpleBrokerMessageHandler} finishes processing the SUBSCRIBE.
     * Also cleaned up on session disconnect via {@link #removeSession(String)}.
     */
    private final ConcurrentHashMap<String, Set<String>> pendingSubscriptions =
            new ConcurrentHashMap<>();

    /**
     * Per-session buffer of SEND frames held while subscriptions are pending.
     * Key: STOMP session ID. Value: ordered list of buffered SEND messages.
     *
     * <p>Populated in {@link #preSend} when a SEND arrives while the session
     * has pending subscriptions. Released in {@link #afterMessageHandled}
     * when the corresponding SUBSCRIBE registration completes.
     */
    private final ConcurrentHashMap<String, List<Message<?>>> bufferedSends =
            new ConcurrentHashMap<>();

    /**
     * Records a SUBSCRIBE destination as pending for the given session.
     *
     * @param sessionId  the STOMP session ID
     * @param destination the SUBSCRIBE destination (e.g. {@code /topic/public})
     */
    void addPendingSubscription(String sessionId, String destination) {
        pendingSubscriptions
                .computeIfAbsent(sessionId, k -> ConcurrentHashMap.newKeySet())
                .add(destination);
        log.debug("Subscription pending: session={} dest={}", sessionId, destination);
    }

    /**
     * Removes a subscription destination from the pending set for a session.
     *
     * @param sessionId  the STOMP session ID
     * @param destination the destination that was subscribed to
     */
    void removePendingSubscription(String sessionId, String destination) {
        Set<String> pending = pendingSubscriptions.get(sessionId);
        if (pending != null) {
            pending.remove(destination);
            if (pending.isEmpty()) {
                pendingSubscriptions.remove(sessionId);
            }
        }
        log.debug("Subscription confirmed: session={} dest={}", sessionId, destination);
    }

    /**
     * Returns true if the given session has any pending subscription registrations.
     *
     * @param sessionId the STOMP session ID
     * @return true if at least one subscription is pending for this session
     */
    boolean hasPendingSubscriptions(String sessionId) {
        Set<String> pending = pendingSubscriptions.get(sessionId);
        return pending != null && !pending.isEmpty();
    }

    /**
     * Buffers a SEND message for later release.
     *
     * @param sessionId the STOMP session ID
     * @param message   the SEND message to buffer
     */
    void bufferSend(String sessionId, Message<?> message) {
        bufferedSends
                .computeIfAbsent(sessionId, k -> new CopyOnWriteArrayList<>())
                .add(message);
        log.debug("SEND buffered: session={} pendingCount={}",
                sessionId, bufferedSends.get(sessionId).size());
    }

    /**
     * Releases all buffered SENDs for the given session by re-dispatching
     * them through the {@code clientInboundChannel}.
     *
     * <p>Re-dispatch is asynchronous: each SEND becomes a new executor task.
     * The current thread is not blocked.
     *
     * @param sessionId the STOMP session ID
     * @param channel   the {@code clientInboundChannel} to re-dispatch through
     */
    void releaseBufferedSends(String sessionId, MessageChannel channel) {
        List<Message<?>> sends = bufferedSends.remove(sessionId);
        if (sends == null || sends.isEmpty()) {
            return;
        }

        log.debug("Releasing {} buffered SENDs for session={}", sends.size(), sessionId);

        for (Message<?> msg : sends) {
            try {
                channel.send(msg);
            } catch (Exception e) {
                log.warn("Failed to re-dispatch buffered SEND for session={}: {}",
                        sessionId, e.getMessage());
            }
        }
    }

    /**
     * Cleans up all state for a session. Called on WebSocket disconnect.
     *
     * @param sessionId the STOMP session ID
     */
    void removeSession(String sessionId) {
        pendingSubscriptions.remove(sessionId);
        bufferedSends.remove(sessionId);
        log.debug("Session cleaned up: session={}", sessionId);
    }

    // ----------------------------------------------------------------
    // ChannelInterceptor
    // ----------------------------------------------------------------

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            return message;
        }

        StompCommand command = accessor.getCommand();
        if (command == null) {
            return message;
        }

        String sessionId = accessor.getSessionId();
        if (sessionId == null) {
            return message;
        }

        // --- SUBSCRIBE: record pending subscription ---
        if (command == StompCommand.SUBSCRIBE) {
            String destination = accessor.getDestination();
            if (destination != null) {
                addPendingSubscription(sessionId, destination);
            }
            return message;
        }

        // --- SEND: buffer if session has any pending subscription ---
        if (command == StompCommand.SEND) {
            if (hasPendingSubscriptions(sessionId)) {
                bufferSend(sessionId, message);
                return null;
            }
        }

        return message;
    }

    // ----------------------------------------------------------------
    // ExecutorChannelInterceptor
    // ----------------------------------------------------------------

    /**
     * Fires after each handler on the {@code clientInboundChannel} completes
     * processing a message. When the handler is
     * {@link SimpleBrokerMessageHandler} and the message was a SUBSCRIBE,
     * the subscription has been registered and any buffered SENDs for this
     * session are released.
     *
     * <p>This callback runs on the {@code clientInboundChannelExecutor}
     * thread, after {@code SimpleBrokerMessageHandler.handleMessageInternal()}
     * has returned. The subscription is guaranteed to be in the
     * {@code DefaultSubscriptionRegistry} at this point.
     */
    @Override
    public void afterMessageHandled(
            Message<?> message,
            MessageChannel channel,
            MessageHandler handler,
            Exception ex) {

        if (!(handler instanceof SimpleBrokerMessageHandler)) {
            return;
        }

        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            return;
        }

        if (accessor.getCommand() != StompCommand.SUBSCRIBE) {
            return;
        }

        String sessionId = accessor.getSessionId();
        String destination = accessor.getDestination();
        if (sessionId == null || destination == null) {
            return;
        }

        removePendingSubscription(sessionId, destination);
        releaseBufferedSends(sessionId, channel);
    }
}
