package com.chatApplication.message_service.service;

import com.chatApplication.message_service.config.InstanceIdentity;
import com.chatApplication.message_service.dto.ChatMessage;
import com.chatApplication.message_service.dto.RedisEnvelope;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.Message;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.nio.charset.StandardCharsets;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RedisMessageSubscriber")
class RedisMessageSubscriberTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private InstanceIdentity instanceIdentity;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private RedisMessageSubscriber redisMessageSubscriber;

    private static final String CURRENT_INSTANCE_ID = "instance-a";
    private static final String REMOTE_INSTANCE_ID = "instance-b";

    @BeforeEach
    void setUp() {
        lenient().when(instanceIdentity.getInstanceId())
                .thenReturn(CURRENT_INSTANCE_ID);
    }

    // ================================================================
    // A. Same-instance event → should be SKIPPED
    // ================================================================

    @Test
    @DisplayName("should skip self-originated Redis messages")
    void onMessage_sameInstanceId_skipsDelivery() throws Exception {
        ChatMessage chatMessage = ChatMessage.builder()
                .msgId(1L)
                .senderId("user1")
                .receiverId("user2")
                .content("Hello")
                .status("SENT")
                .build();

        RedisEnvelope envelope = RedisEnvelope.builder()
                .message(chatMessage)
                .sourceInstanceId(CURRENT_INSTANCE_ID)
                .build();

        Message redisMessage = createRedisMessage(envelope);

        redisMessageSubscriber.onMessage(redisMessage, null);

        verify(messagingTemplate, never()).convertAndSendToUser(
                any(), any(), any());
    }

    // ================================================================
    // B. Cross-instance event → should be DELIVERED
    // ================================================================

    @Test
    @DisplayName("should deliver remote-originated Redis messages")
    void onMessage_differentInstanceId_deliversMessage() throws Exception {
        ChatMessage chatMessage = ChatMessage.builder()
                .msgId(2L)
                .senderId("user3")
                .receiverId("user4")
                .content("Remote message")
                .status("SENT")
                .build();

        RedisEnvelope envelope = RedisEnvelope.builder()
                .message(chatMessage)
                .sourceInstanceId(REMOTE_INSTANCE_ID)
                .build();

        Message redisMessage = createRedisMessage(envelope);

        redisMessageSubscriber.onMessage(redisMessage, null);

        verify(messagingTemplate).convertAndSendToUser(
                eq("user4"),
                eq("/queue/messages"),
                eq(chatMessage));
    }

    // ================================================================
    // C. Multiple remote events → all delivered
    // ================================================================

    @Test
    @DisplayName("should deliver N remote messages")
    void onMessage_multipleRemoteEvents_deliversAll() throws Exception {
        for (int i = 0; i < 5; i++) {
            ChatMessage chatMessage = ChatMessage.builder()
                    .msgId((long) (100 + i))
                    .senderId("sender")
                    .receiverId("receiver")
                    .content("msg-" + i)
                    .status("SENT")
                    .build();

            RedisEnvelope envelope = RedisEnvelope.builder()
                    .message(chatMessage)
                    .sourceInstanceId(REMOTE_INSTANCE_ID)
                    .build();

            Message redisMessage = createRedisMessage(envelope);
            redisMessageSubscriber.onMessage(redisMessage, null);
        }

        verify(messagingTemplate, times(5)).convertAndSendToUser(
                eq("receiver"),
                eq("/queue/messages"),
                any(ChatMessage.class));
    }

    // ================================================================
    // D. Mixed local and remote → only remote delivered
    // ================================================================

    @Test
    @DisplayName("should deliver only remote events in mixed sequence")
    void onMessage_mixedSequence_deliversOnlyRemote() throws Exception {
        // Sequence: local, remote, local, remote, remote
        boolean[] isRemote = {false, true, false, true, true};

        for (int i = 0; i < isRemote.length; i++) {
            ChatMessage chatMessage = ChatMessage.builder()
                    .msgId((long) (200 + i))
                    .senderId("sender")
                    .receiverId("receiver")
                    .content("msg-" + i)
                    .status("SENT")
                    .build();

            RedisEnvelope envelope = RedisEnvelope.builder()
                    .message(chatMessage)
                    .sourceInstanceId(isRemote[i] ? REMOTE_INSTANCE_ID : CURRENT_INSTANCE_ID)
                    .build();

            Message redisMessage = createRedisMessage(envelope);
            redisMessageSubscriber.onMessage(redisMessage, null);
        }

        // 3 remote messages should be delivered (indices 1, 3, 4)
        verify(messagingTemplate, times(3)).convertAndSendToUser(
                eq("receiver"),
                eq("/queue/messages"),
                any(ChatMessage.class));
    }

    // ================================================================
    // E. Null sourceInstanceId → delivered (backward compatibility)
    // ================================================================

    @Test
    @DisplayName("should deliver messages with null sourceInstanceId")
    void onMessage_nullSourceInstanceId_deliversMessage() throws Exception {
        ChatMessage chatMessage = ChatMessage.builder()
                .msgId(3L)
                .senderId("user5")
                .receiverId("user6")
                .content("Legacy message")
                .status("SENT")
                .build();

        RedisEnvelope envelope = RedisEnvelope.builder()
                .message(chatMessage)
                .sourceInstanceId(null)
                .build();

        Message redisMessage = createRedisMessage(envelope);

        redisMessageSubscriber.onMessage(redisMessage, null);

        verify(messagingTemplate).convertAndSendToUser(
                eq("user6"),
                eq("/queue/messages"),
                eq(chatMessage));
    }

    // ================================================================
    // E2. Legacy raw ChatMessage format → delivered (backward compat)
    // ================================================================

    @Test
    @DisplayName("should deliver legacy raw ChatMessage payloads")
    void onMessage_legacyRawChatMessage_deliversMessage() throws Exception {
        ChatMessage chatMessage = ChatMessage.builder()
                .msgId(4L)
                .senderId("user7")
                .receiverId("user8")
                .content("Legacy raw message")
                .status("SENT")
                .build();

        // Serialize as bare ChatMessage (no RedisEnvelope wrapper)
        String jsonPayload = objectMapper.writeValueAsString(chatMessage);
        Message redisMessage = createRedisMessageFromString(jsonPayload);

        redisMessageSubscriber.onMessage(redisMessage, null);

        verify(messagingTemplate).convertAndSendToUser(
                eq("user8"),
                eq("/queue/messages"),
                eq(chatMessage));
    }

    // ================================================================
    // F. Redis failure / malformed payload → error handled gracefully
    // ================================================================

    @Test
    @DisplayName("should handle malformed JSON gracefully")
    void onMessage_malformedPayload_doesNotThrow() {
        Message redisMessage = createRedisMessageFromString("not-valid-json{{{");

        // Should not throw
        redisMessageSubscriber.onMessage(redisMessage, null);

        verify(messagingTemplate, never()).convertAndSendToUser(
                any(), any(), any());
    }

    // ================================================================
    // Helpers
    // ================================================================

    private Message createRedisMessage(Object envelope) throws Exception {
        String json = objectMapper.writeValueAsString(envelope);
        return createRedisMessageFromString(json);
    }

    private Message createRedisMessageFromString(String json) {
        Message message = mock(Message.class);
        when(message.getBody()).thenReturn(json.getBytes(StandardCharsets.UTF_8));
        return message;
    }
}
