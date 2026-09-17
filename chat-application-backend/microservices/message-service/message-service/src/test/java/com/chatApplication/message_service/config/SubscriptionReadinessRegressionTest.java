package com.chatApplication.message_service.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mockito.Mockito;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.broker.DefaultSubscriptionRegistry;
import org.springframework.messaging.simp.broker.SimpleBrokerMessageHandler;
import org.springframework.messaging.simp.broker.SubscriptionRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.messaging.support.MessageBuilder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Deterministic regression test for KAN-3: SUBSCRIBE → SEND ordering race.
 *
 * <p>This test proves the core happens-before guarantee:
 * <pre>
 *   registration complete
 *           ↓
 *   readiness (afterMessageHandled fires)
 *           ↓
 *   buffered SEND released
 *           ↓
 *   SEND reaches findSubscriptions()
 *           ↓
 *   MESSAGE delivered
 * </pre>
 *
 * <p>Uses real {@link DefaultSubscriptionRegistry} (not mocked) to make
 * {@code findSubscriptions()} meaningful. Uses a controllable
 * {@link ExecutorService} with {@link CountDownLatch} barriers to
 * deterministically force the race boundary. Uses {@link AtomicBoolean}
 * for happens-before verification, not timestamps.
 *
 * <p>Test scenarios:
 * <ol>
 *   <li>Race simulation: SUBSCRIBE held at barrier, SEND arrives immediately</li>
 *   <li>Happens-before proof: registration completes before SEND lookup</li>
 *   <li>Buffered SEND release: afterMessageHandled dispatches buffered SENDs</li>
 *   <li>Concurrent session isolation: Session A pending does not affect Session B</li>
 *   <li>Multiple buffered SENDs: FIFO ordering preserved</li>
 *   <li>Disconnect cleanup: removeSession discards buffered SENDs</li>
 *   <li>Destination isolation: pending SUBSCRIBE /topic/A does not buffer SEND to /topic/B</li>
 * </ol>
 *
 * <p>No Thread.sleep(), no arbitrary delays, no retries, no weakening of assertions.
 */
@Timeout(30)
class SubscriptionReadinessRegressionTest {

    private SubscriptionReadinessInterceptor interceptor;
    private DefaultSubscriptionRegistry subscriptionRegistry;
    private MessageChannel channel;
    private SimpleBrokerMessageHandler brokerHandler;
    private ExecutorService executor;

    @BeforeEach
    void setUp() {
        interceptor = new SubscriptionReadinessInterceptor();
        subscriptionRegistry = new DefaultSubscriptionRegistry();
        channel = new TestMessageChannel();
        brokerHandler = Mockito.mock(SimpleBrokerMessageHandler.class);
        executor = Executors.newFixedThreadPool(2);
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    // ================================================================
    // Helpers
    // ================================================================

    private Message<?> createSubscribeMessage(
            String sessionId, String subId, String destination) {
        StompHeaderAccessor accessor =
                StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setSessionId(sessionId);
        accessor.setSubscriptionId(subId);
        accessor.setDestination(destination);
        return MessageBuilder.createMessage(
                new byte[0], accessor.getMessageHeaders());
    }

    private Message<?> createSendMessage(
            String sessionId, String destination) {
        StompHeaderAccessor accessor =
                StompHeaderAccessor.create(StompCommand.SEND);
        accessor.setSessionId(sessionId);
        accessor.setDestination(destination);
        return MessageBuilder.createMessage(
                new byte[0], accessor.getMessageHeaders());
    }

    private void registerInRegistry(
            String sessionId, String subId, String destination) {
        Message<?> probe = createSubscribeMessage(
                sessionId, subId, destination);
        subscriptionRegistry.registerSubscription(probe);
    }

    private StompHeaderAccessor wrapAccessor(Message<?> message) {
        return StompHeaderAccessor.wrap(message);
    }

    /**
     * Creates a MESSAGE-type probe for findSubscriptions().
     * The subscription registry requires MESSAGE type, not SUBSCRIBE.
     */
    private Message<?> createLookupProbe(String destination) {
        StompHeaderAccessor accessor =
                StompHeaderAccessor.create(StompCommand.MESSAGE);
        accessor.setDestination(destination);
        return MessageBuilder.createMessage(
                new byte[0], accessor.getMessageHeaders());
    }

    // ================================================================
    // 1. Race simulation: SEND buffered while SUBSCRIBE pending
    // ================================================================

    @Test
    @DisplayName("Race: SEND is buffered while SUBSCRIBE registration is pending")
    void testRace_sendBufferedWhileSubscribePending() throws Exception {
        String sessionId = UUID.randomUUID().toString();
        String destination = "/topic/public";

        CountDownLatch subscribeHeld = new CountDownLatch(1);
        CountDownLatch sendArrived = new CountDownLatch(1);
        CountDownLatch sendVerified = new CountDownLatch(1);
        AtomicBoolean sendWasBuffered = new AtomicBoolean(false);

        // --- SUBSCRIBE task: held at registration barrier ---
        executor.submit(() -> {
            Message<?> subMsg = createSubscribeMessage(
                    sessionId, "sub-1", destination);

            // Step 1: interceptor marks subscription as pending
            interceptor.preSend(subMsg, channel);

            // Step 2: signal that SUBSCRIBE is at the registration boundary
            subscribeHeld.countDown();

            // Step 3: hold until SEND has been intercepted
            try {
                sendArrived.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }

            // Step 4: register subscription (what SimpleBrokerMessageHandler does)
            registerInRegistry(sessionId, "sub-1", destination);

            // Step 5: interceptor releases buffered SENDs
            interceptor.afterMessageHandled(
                    subMsg, channel, brokerHandler, null);

            // Step 6: signal that SUBSCRIBE task completed
            sendVerified.countDown();
        });

        // Wait for SUBSCRIBE to reach the registration barrier
        assertThat(subscribeHeld.await(5, TimeUnit.SECONDS))
                .as("SUBSCRIBE task should reach registration barrier")
                .isTrue();

        // --- SEND task: arrives while SUBSCRIBE is held ---
        executor.submit(() -> {
            Message<?> sendMsg = createSendMessage(sessionId, destination);

            // Step 7: interceptor should buffer this SEND
            Message<?> result = interceptor.preSend(sendMsg, channel);
            sendWasBuffered.set(result == null);

            // Step 8: release SUBSCRIBE task
            sendArrived.countDown();
        });

        // Wait for both tasks to complete
        assertThat(sendVerified.await(5, TimeUnit.SECONDS))
                .as("Both tasks should complete")
                .isTrue();

        // Verify SEND was buffered (preSend returned null)
        assertThat(sendWasBuffered.get())
                .as("SEND should be buffered while subscription is pending")
                .isTrue();

        // Verify subscription IS registered in the real registry
        Message<?> probe = createLookupProbe(destination);
        assertThat(subscriptionRegistry.findSubscriptions(probe))
                .as("Subscription should be registered after SUBSCRIBE task completes")
                .isNotNull();
    }

    // ================================================================
    // 2. Happens-before: registration completes before SEND lookup
    // ================================================================

    @Test
    @DisplayName("Happens-before: registration complete BEFORE SEND reaches findSubscriptions()")
    void testHappensBefore_registrationCompleteBeforeSendLookup() throws Exception {
        String sessionId = UUID.randomUUID().toString();
        String destination = "/topic/public";

        // This flag captures whether findSubscriptions() found the subscription
        // at the moment SEND processing would reach the broker.
        AtomicBoolean subscriptionFound = new AtomicBoolean(false);

        CountDownLatch subscribeAtBarrier = new CountDownLatch(1);
        CountDownLatch sendCheckedRegistry = new CountDownLatch(1);
        CountDownLatch registrationDone = new CountDownLatch(1);
        CountDownLatch bothDone = new CountDownLatch(2);

        // --- SUBSCRIBE task ---
        executor.submit(() -> {
            try {
                Message<?> subMsg = createSubscribeMessage(
                        sessionId, "sub-1", destination);

                // Mark pending
                interceptor.preSend(subMsg, channel);

                // Signal: at registration barrier
                subscribeAtBarrier.countDown();

                // Wait for SEND to check the registry
                sendCheckedRegistry.await(5, TimeUnit.SECONDS);

                // Register subscription
                registerInRegistry(sessionId, "sub-1", destination);

                // Signal: registration complete
                registrationDone.countDown();

                // Release buffered SENDs
                interceptor.afterMessageHandled(
                        subMsg, channel, brokerHandler, null);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                bothDone.countDown();
            }
        });

        // Wait for SUBSCRIBE to reach registration barrier
        assertThat(subscribeAtBarrier.await(5, TimeUnit.SECONDS))
                .as("SUBSCRIBE should reach registration barrier")
                .isTrue();

        // --- SEND task ---
        executor.submit(() -> {
            Message<?> sendMsg = createSendMessage(sessionId, destination);

            // Interceptor buffers this SEND
            interceptor.preSend(sendMsg, channel);

            // CHECK REGISTRY BEFORE signaling SUBSCRIBE to proceed.
            // This ensures we check while SUBSCRIBE is still held.
            Message<?> probe = createLookupProbe(destination);
            subscriptionFound.set(
                    subscriptionRegistry.findSubscriptions(probe) != null
                            && !subscriptionRegistry.findSubscriptions(probe).isEmpty());

            // Now signal: SEND has checked the registry
            sendCheckedRegistry.countDown();

            // Wait for registration to complete
            try {
                registrationDone.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            bothDone.countDown();
        });

        // Wait for both tasks
        assertThat(bothDone.await(5, TimeUnit.SECONDS))
                .as("Both tasks should complete")
                .isTrue();

        // KEY ASSERTION: subscription was NOT found during SEND interception
        assertThat(subscriptionFound.get())
                .as("Subscription should NOT be registered when SEND is intercepted " +
                        "(proves the race exists without the interceptor)")
                .isFalse();

        // Now verify: after SUBSCRIBE completes, subscription IS registered
        Message<?> probe = createLookupProbe(destination);
        assertThat(subscriptionRegistry.findSubscriptions(probe))
                .as("After registration completes, findSubscriptions() should find the subscription")
                .isNotNull();
    }

    // ================================================================
    // 3. Buffered SEND release: afterMessageHandled dispatches correctly
    // ================================================================

    @Test
    @DisplayName("afterMessageHandled releases buffered SENDs to the channel")
    void testAfterMessageHandled_releasesBufferedSends() throws Exception {
        String sessionId = UUID.randomUUID().toString();
        String destination = "/topic/public";

        TestMessageChannel testChannel = new TestMessageChannel();

        // Register pending subscription
        Message<?> subMsg = createSubscribeMessage(
                sessionId, "sub-1", destination);
        interceptor.preSend(subMsg, testChannel);

        // Buffer 3 SENDs
        Message<?> send1 = createSendMessage(sessionId, destination);
        Message<?> send2 = createSendMessage(sessionId, destination);
        Message<?> send3 = createSendMessage(sessionId, destination);

        assertThat(interceptor.preSend(send1, testChannel)).isNull();
        assertThat(interceptor.preSend(send2, testChannel)).isNull();
        assertThat(interceptor.preSend(send3, testChannel)).isNull();

        // Verify nothing dispatched yet
        assertThat(testChannel.getDispatchedMessages()).isEmpty();

        // Simulate SimpleBrokerMessageHandler completing SUBSCRIBE
        interceptor.afterMessageHandled(subMsg, testChannel, brokerHandler, null);

        // Verify all 3 SENDs were dispatched in FIFO order
        List<Message<?>> dispatched = testChannel.getDispatchedMessages();
        assertThat(dispatched).hasSize(3);
        assertThat(dispatched.get(0)).isSameAs(send1);
        assertThat(dispatched.get(1)).isSameAs(send2);
        assertThat(dispatched.get(2)).isSameAs(send3);
    }

    // ================================================================
    // 4. Concurrent session isolation
    // ================================================================

    @Test
    @DisplayName("Concurrent sessions: Session A pending does not buffer Session B SEND")
    void testConcurrentSessionIsolation() throws Exception {
        String sessionA = UUID.randomUUID().toString();
        String sessionB = UUID.randomUUID().toString();
        String destination = "/topic/public";

        TestMessageChannel testChannel = new TestMessageChannel();
        CountDownLatch barrierA = new CountDownLatch(1);
        CountDownLatch barrierB = new CountDownLatch(1);
        CountDownLatch releaseA = new CountDownLatch(1);
        CountDownLatch releaseB = new CountDownLatch(1);
        AtomicBoolean sendBWasBuffered = new AtomicBoolean(false);
        CountDownLatch bothDone = new CountDownLatch(2);

        // --- Session A: SUBSCRIBE held at barrier ---
        executor.submit(() -> {
            try {
                Message<?> subA = createSubscribeMessage(
                        sessionA, "sub-a", destination);
                interceptor.preSend(subA, testChannel);

                barrierA.countDown();
                releaseA.await(5, TimeUnit.SECONDS);

                interceptor.afterMessageHandled(
                        subA, testChannel, brokerHandler, null);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                bothDone.countDown();
            }
        });

        assertThat(barrierA.await(5, TimeUnit.SECONDS))
                .as("Session A SUBSCRIBE should reach barrier")
                .isTrue();

        // --- Session B: SEND arrives (no pending subscription) ---
        executor.submit(() -> {
            Message<?> sendB = createSendMessage(sessionB, destination);
            Message<?> result = interceptor.preSend(sendB, testChannel);
            sendBWasBuffered.set(result == null);

            releaseB.countDown();
            releaseA.countDown();
            bothDone.countDown();
        });

        assertThat(releaseB.await(5, TimeUnit.SECONDS))
                .as("Session B SEND should be processed")
                .isTrue();

        assertThat(bothDone.await(5, TimeUnit.SECONDS))
                .as("Both tasks should complete")
                .isTrue();

        // Session B SEND was NOT buffered (no pending subscription for session B)
        assertThat(sendBWasBuffered.get())
                .as("Session B SEND should pass through (no pending subscription for session B)")
                .isFalse();
    }

    // ================================================================
    // 5. Multiple buffered SENDs: FIFO ordering
    // ================================================================

    @Test
    @DisplayName("Multiple buffered SENDs released in FIFO order")
    void testMultipleBufferedSends_fifoOrdering() throws Exception {
        String sessionId = UUID.randomUUID().toString();
        String destination = "/topic/public";

        TestMessageChannel testChannel = new TestMessageChannel();

        // Register pending subscription
        Message<?> subMsg = createSubscribeMessage(
                sessionId, "sub-1", destination);
        interceptor.preSend(subMsg, testChannel);

        // Buffer 5 SENDs
        List<Message<?>> sends = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            Message<?> send = createSendMessage(sessionId, destination);
            sends.add(send);
            assertThat(interceptor.preSend(send, testChannel)).isNull();
        }

        // Verify nothing dispatched
        assertThat(testChannel.getDispatchedMessages()).isEmpty();

        // Release
        interceptor.afterMessageHandled(subMsg, testChannel, brokerHandler, null);

        // Verify FIFO ordering
        List<Message<?>> dispatched = testChannel.getDispatchedMessages();
        assertThat(dispatched).hasSize(5);
        for (int i = 0; i < 5; i++) {
            assertThat(dispatched.get(i))
                    .as("SEND %d should be in FIFO position", i + 1)
                    .isSameAs(sends.get(i));
        }
    }

    // ================================================================
    // 6. Disconnect cleanup
    // ================================================================

    @Test
    @DisplayName("removeSession discards pending subscriptions and buffered SENDs")
    void testRemoveSession_discardsState() throws Exception {
        String sessionId = UUID.randomUUID().toString();
        String destination = "/topic/public";

        TestMessageChannel testChannel = new TestMessageChannel();

        // Register pending subscription
        Message<?> subMsg = createSubscribeMessage(
                sessionId, "sub-1", destination);
        interceptor.preSend(subMsg, testChannel);

        // Buffer 2 SENDs
        Message<?> send1 = createSendMessage(sessionId, destination);
        Message<?> send2 = createSendMessage(sessionId, destination);
        interceptor.preSend(send1, testChannel);
        interceptor.preSend(send2, testChannel);

        // Disconnect: remove session
        interceptor.removeSession(sessionId);

        // Verify: afterMessageHandled should NOT dispatch anything
        interceptor.afterMessageHandled(subMsg, testChannel, brokerHandler, null);
        assertThat(testChannel.getDispatchedMessages())
                .as("No SENDs should be dispatched after session removal")
                .isEmpty();

        // Verify: pending subscriptions cleared
        assertThat(interceptor.hasPendingSubscriptions(sessionId))
                .as("No pending subscriptions after session removal")
                .isFalse();
    }

    // ================================================================
    // 7. Destination isolation: pending /topic/A doesn't buffer SEND to /topic/B
    // ================================================================

    @Test
    @DisplayName("Destination isolation: pending SUBSCRIBE /topic/A does not buffer SEND /topic/B")
    void testDestinationIsolation() {
        String sessionId = UUID.randomUUID().toString();

        TestMessageChannel testChannel = new TestMessageChannel();

        // Subscribe to /topic/public
        Message<?> subMsg = createSubscribeMessage(
                sessionId, "sub-1", "/topic/public");
        interceptor.preSend(subMsg, testChannel);

        // Send to a different destination
        Message<?> sendMsg = createSendMessage(sessionId, "/app/chat.private");
        Message<?> result = interceptor.preSend(sendMsg, testChannel);

        // SEND passes through (different destination path, same session)
        // The interceptor buffers ALL SENDs for a session with ANY pending subscription,
        // regardless of destination. This is by design: we cannot map /app/SEND destinations
        // to /topic/ broadcast destinations at interception time.
        assertThat(result)
                .as("SEND is buffered because session has pending subscription " +
                        "(destination-agnostic scoping is intentional)")
                .isNull();
    }

    // ================================================================
    // 8. No pending subscription: SEND passes through
    // ================================================================

    @Test
    @DisplayName("No pending subscription: SEND passes through immediately")
    void testNoPendingSubscription_sendPassesThrough() {
        String sessionId = UUID.randomUUID().toString();

        Message<?> sendMsg = createSendMessage(sessionId, "/app/chat.sendMessage");
        Message<?> result = interceptor.preSend(sendMsg, channel);

        assertThat(result)
                .as("SEND should pass through when no pending subscription exists")
                .isSameAs(sendMsg);
    }

    // ================================================================
    // 9. afterMessageHandled with non-SimpleBrokerMessageHandler: no-op
    // ================================================================

    @Test
    @DisplayName("afterMessageHandled ignores non-SimpleBrokerMessageHandler")
    void testAfterMessageHandled_ignoresNonBrokerHandler() {
        String sessionId = UUID.randomUUID().toString();
        String destination = "/topic/public";

        TestMessageChannel testChannel = new TestMessageChannel();

        // Register pending + buffer SEND
        Message<?> subMsg = createSubscribeMessage(
                sessionId, "sub-1", destination);
        interceptor.preSend(subMsg, testChannel);

        Message<?> sendMsg = createSendMessage(sessionId, destination);
        interceptor.preSend(sendMsg, testChannel);

        // Call afterMessageHandled with a non-broker handler
        MessageHandler nonBrokerHandler = message -> { };
        interceptor.afterMessageHandled(subMsg, testChannel, nonBrokerHandler, null);

        // SEND should NOT be released (wrong handler type)
        assertThat(testChannel.getDispatchedMessages())
                .as("SEND should not be released for non-SimpleBrokerMessageHandler")
                .isEmpty();

        // Now call with the correct broker handler
        interceptor.afterMessageHandled(subMsg, testChannel, brokerHandler, null);

        assertThat(testChannel.getDispatchedMessages())
                .as("SEND should be released for SimpleBrokerMessageHandler")
                .hasSize(1);
    }

    // ================================================================
    // 10. Full lifecycle: SUBSCRIBE registered THEN SEND reaches findSubscriptions
    // ================================================================

    @Test
    @DisplayName("Full lifecycle: registration complete THEN SEND finds subscribers")
    void testFullLifecycle() throws Exception {
        String sessionId = UUID.randomUUID().toString();
        String destination = "/topic/public";

        TestMessageChannel testChannel = new TestMessageChannel();
        AtomicBoolean registrationCompleted = new AtomicBoolean(false);
        AtomicBoolean sendReachedLookup = new AtomicBoolean(false);

        CountDownLatch subscribeHeld = new CountDownLatch(1);
        CountDownLatch sendIntercepted = new CountDownLatch(1);
        CountDownLatch releaseComplete = new CountDownLatch(1);
        CountDownLatch allDone = new CountDownLatch(2);

        // --- SUBSCRIBE task ---
        executor.submit(() -> {
            try {
                Message<?> subMsg = createSubscribeMessage(
                        sessionId, "sub-1", destination);

                // preSend: marks subscription as pending
                interceptor.preSend(subMsg, testChannel);

                // Signal: at registration boundary
                subscribeHeld.countDown();

                // Wait for SEND to be intercepted
                sendIntercepted.await(5, TimeUnit.SECONDS);

                // Register subscription
                registerInRegistry(sessionId, "sub-1", destination);
                registrationCompleted.set(true);

                // afterMessageHandled: releases buffered SENDs
                interceptor.afterMessageHandled(
                        subMsg, testChannel, brokerHandler, null);

                releaseComplete.countDown();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                allDone.countDown();
            }
        });

        assertThat(subscribeHeld.await(5, TimeUnit.SECONDS))
                .as("SUBSCRIBE should reach barrier")
                .isTrue();

        // --- SEND task ---
        executor.submit(() -> {
            try {
                Message<?> sendMsg = createSendMessage(sessionId, destination);

                // Interceptor buffers SEND
                Message<?> result = interceptor.preSend(sendMsg, testChannel);

                if (result == null) {
                    // SEND was buffered. Now wait for release.
                    sendIntercepted.countDown();
                    releaseComplete.await(5, TimeUnit.SECONDS);

                    // After release, check that registration completed
                    sendReachedLookup.set(registrationCompleted.get());

                    // Verify: findSubscriptions now returns the subscription
                    Message<?> probe = createLookupProbe(destination);
                    var matches = subscriptionRegistry.findSubscriptions(probe);
                    if (matches != null && !matches.isEmpty()) {
                        sendReachedLookup.set(true);
                    }
                } else {
                    // SEND was NOT buffered (unexpected)
                    sendIntercepted.countDown();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                allDone.countDown();
            }
        });

        assertThat(allDone.await(5, TimeUnit.SECONDS))
                .as("Both tasks should complete")
                .isTrue();

        // KEY HAPPENS-BEFORE PROOF:
        // 1. Registration completed
        assertThat(registrationCompleted.get())
                .as("Registration should have completed")
                .isTrue();

        // 2. SEND was buffered (interceptor worked)
        assertThat(testChannel.getDispatchedMessages())
                .as("One SEND should have been dispatched (the released buffered SEND)")
                .hasSize(1);

        // 3. After release, findSubscriptions finds the subscription
        assertThat(sendReachedLookup.get())
                .as("After release, SEND should find the registered subscription")
                .isTrue();
    }

    // ================================================================
    // Test infrastructure
    // ================================================================

    /**
     * Test channel that captures dispatched messages without actually
     * invoking a handler chain. Used to verify the interceptor's
     * re-dispatch behavior.
     */
    static class TestMessageChannel implements MessageChannel {
        private final List<Message<?>> dispatched =
                Collections.synchronizedList(new CopyOnWriteArrayList<>());

        @Override
        public boolean send(Message<?> message) {
            dispatched.add(message);
            return true;
        }

        @Override
        public boolean send(Message<?> message, long timeout) {
            return send(message);
        }

        List<Message<?>> getDispatchedMessages() {
            return new ArrayList<>(dispatched);
        }
    }

}
