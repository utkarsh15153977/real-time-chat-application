package com.chatApplication.message_service.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.simp.broker.SimpleBrokerMessageHandler;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ExecutorChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;

import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

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
 * <p>Bounded buffer: The per-session SEND buffer is bounded by
 * {@code maxBufferedMessages} (default 100). When the buffer is full, the
 * newest SEND is dropped with a WARN log and an overflow counter is
 * incremented. A periodic sweep discards sessions whose buffer has been
 * held for longer than {@code bufferTimeoutMs} (default 5000ms), preventing
 * unbounded memory growth from stalled subscriptions.
 *
 * <p>Threading: All data structures are lock-free ({@link ConcurrentHashMap},
 * {@link ArrayDeque}). Buffered SENDs are re-dispatched asynchronously via
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

    // ------------------------------------------------------------
    // Configuration (injected from application properties)
    // ------------------------------------------------------------

    /** Maximum number of SEND messages to buffer per session before overflow. */
    @Value("${chat.subscription-readiness.max-buffered-messages:100}")
    private int maxBufferedMessages;

    /** Maximum time (ms) a session's SEND buffer is held before stale discard. */
    @Value("${chat.subscription-readiness.buffer-timeout-ms:5000}")
    private long bufferTimeoutMs;

    // ------------------------------------------------------------
    // Internal state
    // ------------------------------------------------------------

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
     * Key: STOMP session ID. Value: ordered deque of buffered SEND messages.
     *
     * <p>Bounded by {@link #maxBufferedMessages}. When full, the newest SEND
     * is dropped (overflow).
     *
     * <p>Populated in {@link #preSend} when a SEND arrives while the session
     * has pending subscriptions. Released in {@link #afterMessageHandled}
     * when all subscription registrations complete (or discarded on failure).
     */
    private final ConcurrentHashMap<String, Deque<Message<?>>> bufferedSends =
            new ConcurrentHashMap<>();

    /**
     * Per-session timestamp (millis) when the first SEND was buffered.
     * Used by the periodic sweep to detect and discard stale sessions.
     *
     * <p>Set in {@link #bufferSend}. Cleaned up on release, discard, or
     * session removal.
     */
    private final ConcurrentHashMap<String, Long> bufferTimestamps =
            new ConcurrentHashMap<>();

    // ------------------------------------------------------------
    // Metrics (simple counters)
    // ------------------------------------------------------------

    /** Total number of SENDs that were buffered and later released. */
    private final AtomicLong releasedCount = new AtomicLong(0);

    /** Total number of SENDs discarded due to subscription failure. */
    private final AtomicLong discardedCount = new AtomicLong(0);

    /** Total number of SENDs dropped due to buffer overflow. */
    private final AtomicLong overflowCount = new AtomicLong(0);

    /** Total number of sessions discarded by the timeout sweep. */
    private final AtomicLong staleSweepCount = new AtomicLong(0);

    // ------------------------------------------------------------
    // Periodic sweep executor
    // ------------------------------------------------------------

    /**
     * Single-thread executor for periodic stale-buffer cleanup.
     * Daemon threads so it does not prevent JVM shutdown.
     */
    private final ScheduledExecutorService cleanupExecutor =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "SubReadiness-cleanup");
                t.setDaemon(true);
                return t;
            });

    /**
     * Package-private constructor for unit testing. Allows callers to set
     * buffer limits directly without needing a Spring context for {@code @Value}.
     *
     * @param maxBufferedMessages maximum SENDs to buffer per session
     * @param bufferTimeoutMs    maximum time (ms) to hold a buffered session
     */
    SubscriptionReadinessInterceptor(int maxBufferedMessages, long bufferTimeoutMs) {
        this.maxBufferedMessages = maxBufferedMessages;
        this.bufferTimeoutMs = bufferTimeoutMs;
        cleanupExecutor.scheduleWithFixedDelay(
                this::discardStaleSessions,
                2, 2, TimeUnit.SECONDS);
    }

    /**
     * Default constructor for Spring dependency injection.
     * {@code @Value} fields are injected by Spring after construction.
     * The periodic sweep is started after field injection completes.
     */
    public SubscriptionReadinessInterceptor() {
        cleanupExecutor.scheduleWithFixedDelay(
                this::discardStaleSessions,
                2, 2, TimeUnit.SECONDS);
    }

    // ------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------

    @PreDestroy
    void shutdown() {
        cleanupExecutor.shutdownNow();
        log.info("SubscriptionReadinessInterceptor shutdown. "
                + "released={} discarded={} overflow={} staleSweep={}",
                releasedCount.get(), discardedCount.get(),
                overflowCount.get(), staleSweepCount.get());
    }

    // ------------------------------------------------------------
    // Package-private: pending subscription management
    // ------------------------------------------------------------

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

    // ------------------------------------------------------------
    // Package-private: buffer management
    // ------------------------------------------------------------

    /**
     * Buffers a SEND message for later release.
     *
     * <p>If the buffer is full ({@link #maxBufferedMessages} reached), the
     * newest SEND is dropped (overflow) with a WARN log.
     *
     * @param sessionId the STOMP session ID
     * @param message   the SEND message to buffer
     */
    void bufferSend(String sessionId, Message<?> message) {
        Deque<Message<?>> deque = bufferedSends
                .computeIfAbsent(sessionId, k -> new ArrayDeque<>());

        if (deque.size() >= maxBufferedMessages) {
            overflowCount.incrementAndGet();
            log.warn("SEND buffer overflow: session={} bufferSize={} max={}. "
                    + "Dropping newest SEND. totalOverflows={}",
                    sessionId, deque.size(), maxBufferedMessages,
                    overflowCount.get());
            return;
        }

        deque.addLast(message);

        // Record timestamp on first buffer entry for this session
        bufferTimestamps.putIfAbsent(sessionId, System.currentTimeMillis());

        log.debug("SEND buffered: session={} bufferSize={}",
                sessionId, deque.size());
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
        Deque<Message<?>> deque = bufferedSends.remove(sessionId);
        bufferTimestamps.remove(sessionId);

        if (deque == null || deque.isEmpty()) {
            return;
        }

        int count = deque.size();
        log.debug("Releasing {} buffered SENDs for session={}", count, sessionId);

        for (Message<?> msg : deque) {
            try {
                channel.send(msg);
            } catch (Exception e) {
                log.warn("Failed to re-dispatch buffered SEND for session={}: {}",
                        sessionId, e.getMessage());
            }
        }

        releasedCount.addAndGet(count);
    }

    /**
     * Discards all buffered SENDs for the given session without
     * re-dispatching them. Used when a subscription fails and the
     * buffered SENDs cannot be delivered.
     *
     * @param sessionId the STOMP session ID
     */
    void discardBufferedSends(String sessionId) {
        Deque<Message<?>> removed = bufferedSends.remove(sessionId);
        bufferTimestamps.remove(sessionId);

        if (removed != null && !removed.isEmpty()) {
            int count = removed.size();
            discardedCount.addAndGet(count);
            log.warn("Discarding {} buffered SENDs for session={} (subscription failed). "
                    + "totalDiscarded={}",
                    count, sessionId, discardedCount.get());
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

        Deque<Message<?>> removed = bufferedSends.remove(sessionId);
        bufferTimestamps.remove(sessionId);

        if (removed != null && !removed.isEmpty()) {
            discardedCount.addAndGet(removed.size());
        }

        log.debug("Session cleaned up: session={}", sessionId);
    }

    // ------------------------------------------------------------
    // Periodic sweep: discard stale buffered sessions
    // ------------------------------------------------------------

    /**
     * Discards sessions whose SEND buffer has been held for longer than
     * {@link #bufferTimeoutMs}. Called periodically by {@link #cleanupExecutor}.
     *
     * <p>Prevents unbounded memory growth when a subscription stalls
     * (e.g., broker never calls {@code afterMessageHandled}).
     */
    void discardStaleSessions() {
        long now = System.currentTimeMillis();

        for (Map.Entry<String, Long> entry : bufferTimestamps.entrySet()) {
            String sessionId = entry.getKey();
            long bufferedAt = entry.getValue();

            if (now - bufferedAt > bufferTimeoutMs) {
                staleSweepCount.incrementAndGet();
                log.warn("Stale session sweep: discarding buffer for session={} "
                        + "(held {}ms, timeout={}ms). totalStaleSweeps={}",
                        sessionId, now - bufferedAt, bufferTimeoutMs,
                        staleSweepCount.get());

                // Remove pending state and discard buffer
                pendingSubscriptions.remove(sessionId);
                hasSubscriptionFailure.remove(sessionId);
                discardBufferedSends(sessionId);
            }
        }
    }

    // ------------------------------------------------------------
    // Package-private: metrics accessors (for testing/monitoring)
    // ------------------------------------------------------------

    long getReleasedCount() {
        return releasedCount.get();
    }

    long getDiscardedCount() {
        return discardedCount.get();
    }

    long getOverflowCount() {
        return overflowCount.get();
    }

    long getStaleSweepCount() {
        return staleSweepCount.get();
    }

    // ------------------------------------------------------------
    // ChannelInterceptor
    // ------------------------------------------------------------

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

    // ------------------------------------------------------------
    // ExecutorChannelInterceptor
    // ------------------------------------------------------------

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
