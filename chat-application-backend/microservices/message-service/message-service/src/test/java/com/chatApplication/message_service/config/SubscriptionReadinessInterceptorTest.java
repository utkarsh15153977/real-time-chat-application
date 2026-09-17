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
        interceptor = new SubscriptionReadinessInterceptor();
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

        StompHeaderAccessor subAccessor =
                StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        subAccessor.setSessionId(sessionId);
        subAccessor.setDestination("/topic/public");
        Message<?> subMsg = MessageBuilder.createMessage(
                new byte[0], subAccessor.getMessageHeaders());
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

        StompHeaderAccessor subAccessor =
                StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        subAccessor.setSessionId(sessionId);
        subAccessor.setDestination("/topic/public");
        Message<?> subMsg = MessageBuilder.createMessage(
                new byte[0], subAccessor.getMessageHeaders());
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

        Message<?> subMsg = createStompMessage(
                StompCommand.SUBSCRIBE, sessionId, "/topic/public");
        interceptor.afterMessageHandled(subMsg, channel, brokerHandler, null);

        verify(channel, never()).send(any());
    }

    @Test
    @DisplayName("removeSession() clears pending subscriptions and buffered SENDs")
    void testRemoveSessionClearsState() {
        String sessionId = UUID.randomUUID().toString();

        StompHeaderAccessor subAccessor =
                StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        subAccessor.setSessionId(sessionId);
        subAccessor.setDestination("/topic/public");
        Message<?> subMsg = MessageBuilder.createMessage(
                new byte[0], subAccessor.getMessageHeaders());
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
        StompHeaderAccessor subAccessor =
                StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        subAccessor.setSessionId(session1);
        subAccessor.setDestination("/topic/public");
        Message<?> subMsg = MessageBuilder.createMessage(
                new byte[0], subAccessor.getMessageHeaders());
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

        StompHeaderAccessor subAccessor =
                StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        subAccessor.setSessionId(sessionId);
        subAccessor.setDestination("/topic/public");
        Message<?> subMsg = MessageBuilder.createMessage(
                new byte[0], subAccessor.getMessageHeaders());
        interceptor.preSend(subMsg, channel);

        // Another SUBSCRIBE passes through
        StompHeaderAccessor sub2Accessor =
                StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        sub2Accessor.setSessionId(sessionId);
        sub2Accessor.setDestination("/topic/private");
        Message<?> sub2Msg = MessageBuilder.createMessage(
                new byte[0], sub2Accessor.getMessageHeaders());
        Message<?> result = interceptor.preSend(sub2Msg, channel);

        assertThat(result).isSameAs(sub2Msg);
    }

    @Test
    @DisplayName("Multiple buffered SENDs are released in FIFO order")
    void testMultipleBufferedSendsReleasedInOrder() {
        String sessionId = UUID.randomUUID().toString();

        StompHeaderAccessor subAccessor =
                StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        subAccessor.setSessionId(sessionId);
        subAccessor.setDestination("/topic/public");
        Message<?> subMsg = MessageBuilder.createMessage(
                new byte[0], subAccessor.getMessageHeaders());
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
        StompHeaderAccessor sub1 =
                StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        sub1.setSessionId(session1);
        sub1.setDestination("/topic/public");
        interceptor.preSend(
                MessageBuilder.createMessage(new byte[0], sub1.getMessageHeaders()),
                channel);

        StompHeaderAccessor sub2 =
                StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        sub2.setSessionId(session2);
        sub2.setDestination("/topic/public");
        interceptor.preSend(
                MessageBuilder.createMessage(new byte[0], sub2.getMessageHeaders()),
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
                MessageBuilder.createMessage(
                        new byte[0], sub2.getMessageHeaders()),
                channel, brokerHandler, null);

        ArgumentCaptor<Message<?>> captor =
                ArgumentCaptor.forClass(Message.class);
        verify(channel, times(1)).send(captor.capture());

        assertThat(captor.getValue()).isSameAs(send2);
    }
}
