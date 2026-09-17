package com.chatApplication.message_service.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
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
     * Per-session set of subscription IDs with pending registration operations.
     * Key: STOMP session ID. Value: set of subscription IDs being registered.
     *
     * <p>Spring uniquely identifies subscriptions by (sessionId, subscriptionId).
     * Two SUBSCRIBEs with different subscription IDs to the same destination are
     * independent registration operations that must be tracked independently.
     *
     * <p>Populated in {@link #preSend} when a SUBSCRIBE frame arrives.
     * Cleaned up in {@link #afterMessageHandled} when
     * {@link SimpleBrokerMessageHandler} finishes processing the SUBSCRIBE.
     * Also cleaned up on session disconnect via {@link #removeSession(String)}.
     */
    private final ConcurrentHashMap<String, Set<String>> pendingSubscriptions =
            new ConcurrentHashMap<>();

    /**
     * Per-session flag indicating whether any subscription for this session
     * has failed. When set, buffered SENDs are discarded rather than released,
     * because the session may be missing a required subscription.
     *
     * <p>Set in {@link #afterMessageHandled} when {@code ex != null}.
     * Cleaned up on session disconnect via {@link #removeSession(String)}.
     */
    private final ConcurrentHashMap<String, Boolean> hasSubscriptionFailure =
            new ConcurrentHashMap<>();

    /**
     * Per-session buffer of SEND frames held while subscriptions are pending.
     * Key: STOMP session ID. Value: ordered list of buffered SEND messages.
     *
     * <p>Populated in {@link #preSend} when a SEND arrives while the session
     * has pending subscriptions. Released in {@link #afterMessageHandled}
     * when all subscription registrations complete (or discarded on failure).
     */
    private final ConcurrentHashMap<String, List<Message<?>>> bufferedSends =
            new ConcurrentHashMap<>();

    /**
     * Records a subscription ID as pending for the given session.
     *
     * @param sessionId      the STOMP session ID
     * @param subscriptionId the STOMP subscription ID from the SUBSCRIBE frame
     */
    void addPendingSubscription(String sessionId, String subscriptionId) {
        pendingSubscriptions
                .computeIfAbsent(sessionId, k -> ConcurrentHashMap.newKeySet())
                .add(subscriptionId);
        log.debug("Subscription pending: session={} subId={}", sessionId, subscriptionId);
    }

    /**
     * Removes a subscription ID from the pending set for a session.
     *
     * @param sessionId      the STOMP session ID
     * @param subscriptionId the subscription ID that was registered
     */
    void removePendingSubscription(String sessionId, String subscriptionId) {
        Set<String> pending = pendingSubscriptions.get(sessionId);
        if (pending != null) {
            pending.remove(subscriptionId);
            if (pending.isEmpty()) {
                pendingSubscriptions.remove(sessionId);
            }
        }
        log.debug("Subscription confirmed: session={} subId={}", sessionId, subscriptionId);
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
     * Discards all buffered SENDs for the given session without
     * re-dispatching them. Used when a subscription fails and the
     * buffered SENDs cannot be delivered.
     *
     * @param sessionId the STOMP session ID
     */
    void discardBufferedSends(String sessionId) {
        List<Message<?>> removed = bufferedSends.remove(sessionId);
        if (removed != null && !removed.isEmpty()) {
            log.warn("Discarding {} buffered SENDs for session={} (subscription failed)",
                    removed.size(), sessionId);
        }
    }

    /**
     * Cleans up all state for a session. Called on WebSocket disconnect.
     *
     * @param sessionId the STOMP session ID
     */
    void removeSession(String sessionId) {
        pendingSubscriptions.remove(sessionId);
        hasSubscriptionFailure.remove(sessionId);
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
            String subscriptionId = accessor.getSubscriptionId();
            if (subscriptionId != null) {
                addPendingSubscription(sessionId, subscriptionId);
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
     * the subscription registration has either completed or failed.
     *
     * <p>On success ({@code ex == null}): the subscription is guaranteed to
     * be in the {@code DefaultSubscriptionRegistry}. If this was the last
     * pending subscription for the session, buffered SENDs are released.
     *
     * <p>On failure ({@code ex != null}): the subscription was NOT registered.
     * The pending destination is removed, but buffered SENDs are NOT released
     * because they may depend on other subscriptions still being registered.
     * If no other subscriptions remain pending, the buffered SENDs are
     * discarded (they cannot be delivered without the failed subscription).
     *
     * <p>This callback runs on the {@code clientInboundChannelExecutor}
     * thread, after {@code SimpleBrokerMessageHandler.handleMessageInternal()}
     * has returned.
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
        String subscriptionId = accessor.getSubscriptionId();
        if (sessionId == null || subscriptionId == null) {
            return;
        }

        if (ex != null) {
            log.warn("Subscription FAILED: session={} subId={} error={}",
                    sessionId, subscriptionId, ex.getMessage());
            hasSubscriptionFailure.put(sessionId, Boolean.TRUE);
            removePendingSubscription(sessionId, subscriptionId);
            if (!hasPendingSubscriptions(sessionId)) {
                discardBufferedSends(sessionId);
            }
            return;
        }

        removePendingSubscription(sessionId, subscriptionId);

        if (!hasPendingSubscriptions(sessionId)) {
            if (Boolean.TRUE.equals(hasSubscriptionFailure.remove(sessionId))) {
                discardBufferedSends(sessionId);
            } else {
                releaseBufferedSends(sessionId, channel);
            }
        }
    }
}
