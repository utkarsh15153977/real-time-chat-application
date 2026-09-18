package com.chatApplication.message_service.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.simp.broker.SimpleBrokerMessageHandler;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link SubscriptionReadinessInterceptor}.
 * <p>
 * Validates the KAN-3 fix: SUBSCRIBE -> SEND ordering guarantee.
 * <p>
 * Tests:
 * - SEND passes through when session has no pending subscriptions
 * - SEND is buffered when session has pending subscriptions
 * - afterMessageHandled releases buffered SENDs
 * - Session cleanup via removeSession() clears state
 * - Multiple sessions are independent
 * - Non-SEND frames pass through even with pending subscriptions
 * - Multiple buffered SENDs are released in order
 */
@ExtendWith(MockitoExtension.class)
class SubscriptionReadinessInterceptorTest {

    private SubscriptionReadinessInterceptor interceptor;
    private MessageChannel channel;
    private SimpleBrokerMessageHandler brokerHandler;

    @BeforeEach
    void setUp() {
        interceptor = new SubscriptionReadinessInterceptor(100, 5000);
        channel = mock(MessageChannel.class);
        brokerHandler = mock(SimpleBrokerMessageHandler.class);
    }

    private Message<?> createStompMessage(StompCommand command,
                                          String sessionId,
                                          String destination) {
        StompHeaderAccessor accessor =
                StompHeaderAccessor.create(command);
        accessor.setSessionId(sessionId);
        if (destination != null) {
            accessor.setDestination(destination);
        }
        return MessageBuilder.createMessage(
                new byte[0], accessor.getMessageHeaders());
    }

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

    @Test
    @DisplayName("SEND passes through when session has no pending subscriptions")
    void testSendPassesThroughWhenNoPendingSubscription() {
        String sessionId = UUID.randomUUID().toString();
        Message<?> sendMsg = createStompMessage(
                StompCommand.SEND, sessionId, "/app/chat.sendMessage");

        Message<?> result = interceptor.preSend(sendMsg, channel);

        assertThat(result).isSameAs(sendMsg);
    }

    @Test
    @DisplayName("SEND is buffered when session has pending subscription")
    void testSendIsBufferedWhenPendingSubscriptionExists() {
        String sessionId = UUID.randomUUID().toString();

        Message<?> subMsg = createSubscribeMessage(
                sessionId, "sub-0", "/topic/public");
        interceptor.preSend(subMsg, channel);

        Message<?> sendMsg = createStompMessage(
                StompCommand.SEND, sessionId, "/app/chat.sendMessage");
        Message<?> result = interceptor.preSend(sendMsg, channel);

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("afterMessageHandled releases buffered SENDs to channel")
    void testAfterMessageHandledReleasesBufferedSends() {
        String sessionId = UUID.randomUUID().toString();

        Message<?> subMsg = createSubscribeMessage(
                sessionId, "sub-0", "/topic/public");
        interceptor.preSend(subMsg, channel);

        Message<?> sendMsg = createStompMessage(
                StompCommand.SEND, sessionId, "/app/chat.sendMessage");
        interceptor.preSend(sendMsg, channel);

        interceptor.afterMessageHandled(subMsg, channel, brokerHandler, null);

        ArgumentCaptor<Message<?>> captor =
                ArgumentCaptor.forClass(Message.class);
        verify(channel, times(1)).send(captor.capture());

        assertThat(captor.getValue()).isSameAs(sendMsg);
    }

    @Test
    @DisplayName("afterMessageHandled passes SEND through for sessions with no buffer")
    void testAfterMessageHandledPassesThroughWhenNoBuffer() {
        String sessionId = UUID.randomUUID().toString();

        Message<?> subMsg = createSubscribeMessage(
                sessionId, "sub-0", "/topic/public");
        interceptor.afterMessageHandled(subMsg, channel, brokerHandler, null);

        verify(channel, never()).send(any());
    }

    @Test
    @DisplayName("removeSession() clears pending subscriptions and buffered SENDs")
    void testRemoveSessionClearsState() {
        String sessionId = UUID.randomUUID().toString();

        Message<?> subMsg = createSubscribeMessage(
                sessionId, "sub-0", "/topic/public");
        interceptor.preSend(subMsg, channel);

        Message<?> sendMsg = createStompMessage(
                StompCommand.SEND, sessionId, "/app/chat.sendMessage");
        interceptor.preSend(sendMsg, channel);

        interceptor.removeSession(sessionId);

        // After removeSession, afterMessageHandled should not dispatch
        interceptor.afterMessageHandled(subMsg, channel, brokerHandler, null);

        verify(channel, never()).send(any());
    }

    @Test
    @DisplayName("Multiple sessions are independent")
    void testMultipleSessionsAreIndependent() {
        String session1 = UUID.randomUUID().toString();
        String session2 = UUID.randomUUID().toString();

        // Register pending subscription for session1 only
        Message<?> subMsg = createSubscribeMessage(
                session1, "sub-0", "/topic/public");
        interceptor.preSend(subMsg, channel);

        // SEND from session1 is buffered
        Message<?> send1 = createStompMessage(
                StompCommand.SEND, session1, "/app/chat.sendMessage");
        assertThat(interceptor.preSend(send1, channel)).isNull();

        // SEND from session2 passes through (no pending subscription)
        Message<?> send2 = createStompMessage(
                StompCommand.SEND, session2, "/app/chat.sendMessage");
        assertThat(interceptor.preSend(send2, channel)).isSameAs(send2);
    }

    @Test
    @DisplayName("Non-SEND frames pass through even with pending subscription")
    void testNonSendFramesPassThroughWithPendingSubscription() {
        String sessionId = UUID.randomUUID().toString();

        Message<?> subMsg = createSubscribeMessage(
                sessionId, "sub-0", "/topic/public");
        interceptor.preSend(subMsg, channel);

        // Another SUBSCRIBE passes through
        Message<?> sub2Msg = createSubscribeMessage(
                sessionId, "sub-1", "/topic/private");
        Message<?> result = interceptor.preSend(sub2Msg, channel);

        assertThat(result).isSameAs(sub2Msg);
    }

    @Test
    @DisplayName("Multiple buffered SENDs are released in FIFO order")
    void testMultipleBufferedSendsReleasedInOrder() {
        String sessionId = UUID.randomUUID().toString();

        Message<?> subMsg = createSubscribeMessage(
                sessionId, "sub-0", "/topic/public");
        interceptor.preSend(subMsg, channel);

        Message<?> send1 = createStompMessage(
                StompCommand.SEND, sessionId, "/app/chat.sendMessage");
        Message<?> send2 = createStompMessage(
                StompCommand.SEND, sessionId, "/app/chat.sendMessage");
        Message<?> send3 = createStompMessage(
                StompCommand.SEND, sessionId, "/app/chat.sendMessage");

        interceptor.preSend(send1, channel);
        interceptor.preSend(send2, channel);
        interceptor.preSend(send3, channel);

        interceptor.afterMessageHandled(subMsg, channel, brokerHandler, null);

        ArgumentCaptor<Message<?>> captor =
                ArgumentCaptor.forClass(Message.class);
        verify(channel, times(3)).send(captor.capture());

        List<Message<?>> dispatched = captor.getAllValues();
        assertThat(dispatched).containsExactly(send1, send2, send3);
    }

    @Test
    @DisplayName("removeSession only affects the specified session")
    void testRemoveSessionOnlyAffectsSpecifiedSession() {
        String session1 = UUID.randomUUID().toString();
        String session2 = UUID.randomUUID().toString();

        // Both sessions have pending subscriptions
        interceptor.preSend(
                createSubscribeMessage(session1, "sub-a", "/topic/public"),
                channel);
        interceptor.preSend(
                createSubscribeMessage(session2, "sub-b", "/topic/public"),
                channel);

        // Buffer SENDs for both
        Message<?> send1 = createStompMessage(
                StompCommand.SEND, session1, "/app/chat.sendMessage");
        Message<?> send2 = createStompMessage(
                StompCommand.SEND, session2, "/app/chat.sendMessage");
        interceptor.preSend(send1, channel);
        interceptor.preSend(send2, channel);

        // Remove only session1
        interceptor.removeSession(session1);

        // Release session2's buffered SEND
        interceptor.afterMessageHandled(
                createSubscribeMessage(session2, "sub-b", "/topic/public"),
                channel, brokerHandler, null);

        ArgumentCaptor<Message<?>> captor =
                ArgumentCaptor.forClass(Message.class);
        verify(channel, times(1)).send(captor.capture());

        assertThat(captor.getValue()).isSameAs(send2);
    }

    // ================================================================
    // 10. Failed subscription: buffered SENDs are discarded, not released
    // ================================================================

    @Test
    @DisplayName("afterMessageHandled with exception discards buffered SENDs (not released)")
    void testFailedSubscription_discardsBufferedSends() {
        String sessionId = UUID.randomUUID().toString();

        Message<?> subMsg = createSubscribeMessage(
                sessionId, "sub-0", "/topic/public");
        interceptor.preSend(subMsg, channel);

        Message<?> sendMsg = createStompMessage(
                StompCommand.SEND, sessionId, "/app/chat.sendMessage");
        interceptor.preSend(sendMsg, channel);

        // Simulate failed subscription (ex != null)
        RuntimeException failure = new RuntimeException("broker error");
        interceptor.afterMessageHandled(subMsg, channel, brokerHandler, failure);

        // SEND should NOT be released (subscription failed)
        verify(channel, never()).send(any());
    }

    // ================================================================
    // 11. Multiple subscriptions: SEND only released when ALL complete
    // ================================================================

    @Test
    @DisplayName("Multiple subscriptions: buffered SENDs released only when all subscriptions complete")
    void testMultipleSubscriptions_onlyReleaseWhenAllComplete() {
        String sessionId = UUID.randomUUID().toString();

        Message<?> sub1Msg = createSubscribeMessage(
                sessionId, "sub-0", "/topic/A");
        Message<?> sub2Msg = createSubscribeMessage(
                sessionId, "sub-1", "/topic/B");

        // Both subscriptions pending
        interceptor.preSend(sub1Msg, channel);
        interceptor.preSend(sub2Msg, channel);

        // Buffer a SEND
        Message<?> sendMsg = createStompMessage(
                StompCommand.SEND, sessionId, "/app/chat.sendMessage");
        interceptor.preSend(sendMsg, channel);

        // First subscription completes - SEND should NOT be released
        interceptor.afterMessageHandled(sub1Msg, channel, brokerHandler, null);
        verify(channel, never()).send(any());

        // Second subscription completes - NOW SEND should be released
        interceptor.afterMessageHandled(sub2Msg, channel, brokerHandler, null);

        ArgumentCaptor<Message<?>> captor =
                ArgumentCaptor.forClass(Message.class);
        verify(channel, times(1)).send(captor.capture());
        assertThat(captor.getValue()).isSameAs(sendMsg);
    }

    // ================================================================
    // 12. One subscription fails, one succeeds: SENDs discarded
    // ================================================================

    @Test
    @DisplayName("One subscription fails, one succeeds: buffered SENDs discarded")
    void testOneFailOneSuccess_discardsBufferedSends() {
        String sessionId = UUID.randomUUID().toString();

        Message<?> sub1Msg = createSubscribeMessage(
                sessionId, "sub-0", "/topic/A");
        Message<?> sub2Msg = createSubscribeMessage(
                sessionId, "sub-1", "/topic/B");

        // Both subscriptions pending
        interceptor.preSend(sub1Msg, channel);
        interceptor.preSend(sub2Msg, channel);

        // Buffer a SEND
        Message<?> sendMsg = createStompMessage(
                StompCommand.SEND, sessionId, "/app/chat.sendMessage");
        interceptor.preSend(sendMsg, channel);

        // First subscription fails - SEND should NOT be released
        interceptor.afterMessageHandled(sub1Msg, channel, brokerHandler,
                new RuntimeException("failed"));
        verify(channel, never()).send(any());

        // Second subscription succeeds - no more pending, but previous
        // failure means we should discard (not release) the buffer
        // because the first subscription was never registered.
        interceptor.afterMessageHandled(sub2Msg, channel, brokerHandler, null);

        // SENDs should be discarded, not released
        verify(channel, never()).send(any());
    }

    // ================================================================
    // 13. Duplicate subscription IDs to same destination: independently tracked
    // ================================================================

    @Test
    @DisplayName("Duplicate subscription IDs to same destination: first completion does NOT release SEND")
    void testDuplicateSubIds_sameDestination_firstCompletionDoesNotRelease() {
        String sessionId = UUID.randomUUID().toString();
        String destination = "/topic/public";

        // Two SUBSCRIBEs with different subscription IDs to same destination
        Message<?> sub1Msg = createSubscribeMessage(sessionId, "sub-0", destination);
        Message<?> sub2Msg = createSubscribeMessage(sessionId, "sub-1", destination);

        interceptor.preSend(sub1Msg, channel);
        interceptor.preSend(sub2Msg, channel);

        // Buffer a SEND
        Message<?> sendMsg = createStompMessage(
                StompCommand.SEND, sessionId, "/app/chat.sendMessage");
        interceptor.preSend(sendMsg, channel);

        // First subscription completes - SEND should NOT be released
        interceptor.afterMessageHandled(sub1Msg, channel, brokerHandler, null);
        verify(channel, never()).send(any());

        // Second subscription completes - NOW SEND should be released
        interceptor.afterMessageHandled(sub2Msg, channel, brokerHandler, null);

        ArgumentCaptor<Message<?>> captor =
                ArgumentCaptor.forClass(Message.class);
        verify(channel, times(1)).send(captor.capture());
        assertThat(captor.getValue()).isSameAs(sendMsg);
    }

    // ================================================================
    // 14. One duplicate succeeds, one fails: SENDs discarded
    // ================================================================

    @Test
    @DisplayName("Duplicate subscription: one succeeds, one fails: SENDs discarded")
    void testDuplicateSubIds_oneFailOneSuccess_discardsBufferedSends() {
        String sessionId = UUID.randomUUID().toString();
        String destination = "/topic/public";

        Message<?> sub1Msg = createSubscribeMessage(sessionId, "sub-0", destination);
        Message<?> sub2Msg = createSubscribeMessage(sessionId, "sub-1", destination);

        interceptor.preSend(sub1Msg, channel);
        interceptor.preSend(sub2Msg, channel);

        // Buffer a SEND
        Message<?> sendMsg = createStompMessage(
                StompCommand.SEND, sessionId, "/app/chat.sendMessage");
        interceptor.preSend(sendMsg, channel);

        // First subscription fails
        interceptor.afterMessageHandled(sub1Msg, channel, brokerHandler,
                new RuntimeException("failed"));
        verify(channel, never()).send(any());

        // Second subscription succeeds - failure flag set, should discard
        interceptor.afterMessageHandled(sub2Msg, channel, brokerHandler, null);

        verify(channel, never()).send(any());
    }

    // ================================================================
    // 15. Buffer overflow: SEND is dropped when buffer is full
    // ================================================================

    @Test
    @DisplayName("Buffer overflow: SEND dropped when maxBufferedMessages reached")
    void testBufferOverflow_sendDroppedWhenFull() {
        SubscriptionReadinessInterceptor bounded =
                new SubscriptionReadinessInterceptor(3, 5000);

        String sessionId = UUID.randomUUID().toString();

        Message<?> subMsg = createSubscribeMessage(
                sessionId, "sub-0", "/topic/public");
        bounded.preSend(subMsg, channel);

        // Buffer 3 SENDs (hits the limit of 3)
        Message<?> send1 = createStompMessage(
                StompCommand.SEND, sessionId, "/app/chat.sendMessage");
        Message<?> send2 = createStompMessage(
                StompCommand.SEND, sessionId, "/app/chat.sendMessage");
        Message<?> send3 = createStompMessage(
                StompCommand.SEND, sessionId, "/app/chat.sendMessage");

        assertThat(bounded.preSend(send1, channel)).isNull();
        assertThat(bounded.preSend(send2, channel)).isNull();
        assertThat(bounded.preSend(send3, channel)).isNull();

        // 4th SEND should be dropped (overflow)
        Message<?> send4 = createStompMessage(
                StompCommand.SEND, sessionId, "/app/chat.sendMessage");
        Message<?> result = bounded.preSend(send4, channel);

        assertThat(result)
                .as("Overflow SEND should be dropped (preSend returns null)")
                .isNull();
        assertThat(bounded.getOverflowCount())
                .as("Overflow count should be 1")
                .isEqualTo(1);
    }

    // ================================================================
    // 16. Buffer overflow: only non-overflow SENDs are released
    // ================================================================

    @Test
    @DisplayName("Buffer overflow: only non-overflow SENDs released on subscription complete")
    void testBufferOverflow_onlyNonOverflowSendsReleased() {
        SubscriptionReadinessInterceptor bounded =
                new SubscriptionReadinessInterceptor(2, 5000);

        String sessionId = UUID.randomUUID().toString();

        Message<?> subMsg = createSubscribeMessage(
                sessionId, "sub-0", "/topic/public");
        bounded.preSend(subMsg, channel);

        // Buffer 2 SENDs (hits the limit)
        Message<?> send1 = createStompMessage(
                StompCommand.SEND, sessionId, "/app/chat.sendMessage");
        Message<?> send2 = createStompMessage(
                StompCommand.SEND, sessionId, "/app/chat.sendMessage");

        assertThat(bounded.preSend(send1, channel)).isNull();
        assertThat(bounded.preSend(send2, channel)).isNull();

        // 3rd SEND overflows
        Message<?> send3 = createStompMessage(
                StompCommand.SEND, sessionId, "/app/chat.sendMessage");
        bounded.preSend(send3, channel);

        // Complete subscription
        bounded.afterMessageHandled(subMsg, channel, brokerHandler, null);

        // Only 2 SENDs should be released (the 3rd was dropped)
        ArgumentCaptor<Message<?>> captor =
                ArgumentCaptor.forClass(Message.class);
        verify(channel, times(2)).send(captor.capture());

        assertThat(captor.getAllValues()).containsExactly(send1, send2);
        assertThat(bounded.getOverflowCount()).isEqualTo(1);
        assertThat(bounded.getReleasedCount()).isEqualTo(2);
    }

    // ================================================================
    // 17. Metrics: discardedCount increments on failure discard
    // ================================================================

    @Test
    @DisplayName("Metrics: discardedCount increments when buffered SENDs discarded on failure")
    void testMetrics_discardedCountOnFailure() {
        SubscriptionReadinessInterceptor bounded =
                new SubscriptionReadinessInterceptor(10, 5000);

        String sessionId = UUID.randomUUID().toString();

        Message<?> subMsg = createSubscribeMessage(
                sessionId, "sub-0", "/topic/public");
        bounded.preSend(subMsg, channel);

        Message<?> send1 = createStompMessage(
                StompCommand.SEND, sessionId, "/app/chat.sendMessage");
        Message<?> send2 = createStompMessage(
                StompCommand.SEND, sessionId, "/app/chat.sendMessage");
        bounded.preSend(send1, channel);
        bounded.preSend(send2, channel);

        // Subscription fails
        bounded.afterMessageHandled(subMsg, channel, brokerHandler,
                new RuntimeException("failed"));

        assertThat(bounded.getDiscardedCount())
                .as("discardedCount should be 2")
                .isEqualTo(2);
    }

    // ================================================================
    // 18. Metrics: releasedCount increments on release
    // ================================================================

    @Test
    @DisplayName("Metrics: releasedCount increments when buffered SENDs released")
    void testMetrics_releasedCountOnRelease() {
        SubscriptionReadinessInterceptor bounded =
                new SubscriptionReadinessInterceptor(10, 5000);

        String sessionId = UUID.randomUUID().toString();

        Message<?> subMsg = createSubscribeMessage(
                sessionId, "sub-0", "/topic/public");
        bounded.preSend(subMsg, channel);

        Message<?> send1 = createStompMessage(
                StompCommand.SEND, sessionId, "/app/chat.sendMessage");
        Message<?> send2 = createStompMessage(
                StompCommand.SEND, sessionId, "/app/chat.sendMessage");
        Message<?> send3 = createStompMessage(
                StompCommand.SEND, sessionId, "/app/chat.sendMessage");
        bounded.preSend(send1, channel);
        bounded.preSend(send2, channel);
        bounded.preSend(send3, channel);

        // Subscription succeeds
        bounded.afterMessageHandled(subMsg, channel, brokerHandler, null);

        assertThat(bounded.getReleasedCount())
                .as("releasedCount should be 3")
                .isEqualTo(3);
    }

    // ================================================================
    // 19. Multiple overflows accumulate
    // ================================================================

    @Test
    @DisplayName("Multiple overflows: overflowCount accumulates across SENDs")
    void testMultipleOverflows_accumulate() {
        SubscriptionReadinessInterceptor bounded =
                new SubscriptionReadinessInterceptor(1, 5000);

        String sessionId = UUID.randomUUID().toString();

        Message<?> subMsg = createSubscribeMessage(
                sessionId, "sub-0", "/topic/public");
        bounded.preSend(subMsg, channel);

        // 1st SEND fits (buffer size 1)
        Message<?> send1 = createStompMessage(
                StompCommand.SEND, sessionId, "/app/chat.sendMessage");
        bounded.preSend(send1, channel);

        // 2nd SEND overflows
        Message<?> send2 = createStompMessage(
                StompCommand.SEND, sessionId, "/app/chat.sendMessage");
        bounded.preSend(send2, channel);

        // 3rd SEND overflows
        Message<?> send3 = createStompMessage(
                StompCommand.SEND, sessionId, "/app/chat.sendMessage");
        bounded.preSend(send3, channel);

        assertThat(bounded.getOverflowCount())
                .as("overflowCount should be 2")
                .isEqualTo(2);
    }

    // ================================================================
    // 20. No overflow when buffer has room
    // ================================================================

    @Test
    @DisplayName("No overflow when buffer has room")
    void testNoOverflow_whenBufferHasRoom() {
        SubscriptionReadinessInterceptor bounded =
                new SubscriptionReadinessInterceptor(5, 5000);

        String sessionId = UUID.randomUUID().toString();

        Message<?> subMsg = createSubscribeMessage(
                sessionId, "sub-0", "/topic/public");
        bounded.preSend(subMsg, channel);

        // Buffer 5 SENDs (exactly at limit)
        for (int i = 0; i < 5; i++) {
            Message<?> send = createStompMessage(
                    StompCommand.SEND, sessionId, "/app/chat.sendMessage");
            bounded.preSend(send, channel);
        }

        assertThat(bounded.getOverflowCount())
                .as("No overflow when at exact limit")
                .isEqualTo(0);
    }

    // ================================================================
    // 21. Overflow across multiple sessions is independent
    // ================================================================

    @Test
    @DisplayName("Overflow across sessions: each session has independent buffer limit")
    void testOverflow_independentPerSession() {
        SubscriptionReadinessInterceptor bounded =
                new SubscriptionReadinessInterceptor(2, 5000);

        String session1 = UUID.randomUUID().toString();
        String session2 = UUID.randomUUID().toString();

        // Session 1: 2 SENDs fit
        bounded.preSend(
                createSubscribeMessage(session1, "sub-1", "/topic/public"),
                channel);
        bounded.preSend(createStompMessage(
                StompCommand.SEND, session1, "/app/chat.sendMessage"), channel);
        bounded.preSend(createStompMessage(
                StompCommand.SEND, session1, "/app/chat.sendMessage"), channel);

        // Session 2: also 2 SENDs fit (independent)
        bounded.preSend(
                createSubscribeMessage(session2, "sub-2", "/topic/public"),
                channel);
        bounded.preSend(createStompMessage(
                StompCommand.SEND, session2, "/app/chat.sendMessage"), channel);
        bounded.preSend(createStompMessage(
                StompCommand.SEND, session2, "/app/chat.sendMessage"), channel);

        assertThat(bounded.getOverflowCount())
                .as("No overflow in either session")
                .isEqualTo(0);
    }

    // ================================================================
    // 22. removeSession discards and counts correctly
    // ================================================================

    @Test
    @DisplayName("removeSession: discardedCount increments for buffered SENDs")
    void testRemoveSession_discardedCounted() {
        SubscriptionReadinessInterceptor bounded =
                new SubscriptionReadinessInterceptor(10, 5000);

        String sessionId = UUID.randomUUID().toString();

        bounded.preSend(
                createSubscribeMessage(sessionId, "sub-0", "/topic/public"),
                channel);
        bounded.preSend(createStompMessage(
                StompCommand.SEND, sessionId, "/app/chat.sendMessage"), channel);
        bounded.preSend(createStompMessage(
                StompCommand.SEND, sessionId, "/app/chat.sendMessage"), channel);

        bounded.removeSession(sessionId);

        assertThat(bounded.getDiscardedCount())
                .as("discardedCount should be 2 after removeSession")
                .isEqualTo(2);
    }
}
