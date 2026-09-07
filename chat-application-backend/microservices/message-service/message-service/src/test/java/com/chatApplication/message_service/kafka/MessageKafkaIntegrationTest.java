package com.chatApplication.message_service.kafka;

import com.chatApplication.message_service.controller.ChatWebSocketController;
import com.chatApplication.message_service.dto.ChatMessageRequestDTO;
import com.chatApplication.message_service.dto.ChatMessageResponseDTO;
import com.chatApplication.message_service.entity.MessageType;
import com.chatApplication.message_service.service.InboxService;
import com.chatApplication.message_service.service.MessageService;
import com.chatApplication.message_service.service.PushNotificationService;
import com.chatApplication.message_service.service.UserPresenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.SendResult;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.security.Principal;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MessageKafkaIntegrationTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private MessageService messageService;

    @Mock
    private InboxService inboxService;

    @Mock
    private UserPresenceService userPresenceService;

    @Mock
    private PushNotificationService pushNotificationService;

    @Mock
    private MessageEventPublisher messageEventPublisher;

    @InjectMocks
    private ChatWebSocketController controller;

    private Principal principal;
    private ChatMessageRequestDTO requestDTO;
    private ChatMessageResponseDTO responseDTO;

    @BeforeEach
    void setUp() {
        principal = new UsernamePasswordAuthenticationToken(
                "user1", null,
                List.of(new SimpleGrantedAuthority("ROLE_USER")));

        requestDTO = ChatMessageRequestDTO.builder()
                .senderId("user1")
                .recipientId("user2")
                .chatRoomId("user1-user2")
                .content("Hello World")
                .messageType(MessageType.TEXT)
                .build();

        responseDTO = ChatMessageResponseDTO.builder()
                .messageId("123")
                .senderId("user1")
                .recipientId("user2")
                .chatRoomId("user1-user2")
                .content("Hello World")
                .messageType(MessageType.TEXT)
                .status("SENT")
                .timestamp(Instant.parse("2025-01-15T10:30:00Z"))
                .build();
    }

    private void stubPublisherSuccess() {
        when(messageEventPublisher.publishMessageCreatedEvent(any()))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));
    }

    @Nested
    @DisplayName("Kafka Event Publishing")
    class KafkaEventPublishingTests {

        @Test
        @DisplayName("should publish MessageCreatedEvent after successful message save")
        void sendMessage_messageSaved_publishesKafkaEvent() {
            // Arrange
            when(messageService.saveMessage(requestDTO)).thenReturn(responseDTO);
            stubPublisherSuccess();

            // Act
            controller.sendMessage(requestDTO, principal);

            // Assert
            ArgumentCaptor<MessageCreatedEvent> eventCaptor =
                    ArgumentCaptor.forClass(MessageCreatedEvent.class);
            verify(messageEventPublisher).publishMessageCreatedEvent(eventCaptor.capture());

            MessageCreatedEvent capturedEvent = eventCaptor.getValue();
            assertThat(capturedEvent.getMessageId()).isEqualTo("123");
            assertThat(capturedEvent.getChatRoomId()).isEqualTo("user1-user2");
            assertThat(capturedEvent.getSenderId()).isEqualTo("user1");
            assertThat(capturedEvent.getRecipientId()).isEqualTo("user2");
            assertThat(capturedEvent.getContent()).isEqualTo("Hello World");
            assertThat(capturedEvent.getType()).isEqualTo(MessageType.TEXT);
            assertThat(capturedEvent.getCreatedAt()).isEqualTo(Instant.parse("2025-01-15T10:30:00Z"));
        }

        @Test
        @DisplayName("should still deliver message when Kafka publish fails")
        void sendMessage_kafkaFails_messageStillDelivered() {
            // Arrange
            when(messageService.saveMessage(requestDTO)).thenReturn(responseDTO);
            when(messageEventPublisher.publishMessageCreatedEvent(any()))
                    .thenReturn(failedFuture(new RuntimeException("Kafka broker down")));

            // Act
            controller.sendMessage(requestDTO, principal);

            // Assert - message is still persisted and broadcast
            verify(messageService).saveMessage(requestDTO);
            verify(messagingTemplate).convertAndSend(
                    eq("/topic/room.user1-user2"), eq(responseDTO));
            verify(messagingTemplate).convertAndSendToUser(
                    eq("user2"), eq("/queue/messages"), eq(responseDTO));
        }

        @Test
        @DisplayName("should not publish Kafka event when message save fails")
        void sendMessage_messageSaveFails_noKafkaEvent() {
            // Arrange
            when(messageService.saveMessage(requestDTO))
                    .thenThrow(new RuntimeException("Database error"));

            // Act
            controller.sendMessage(requestDTO, principal);

            // Assert
            verify(messageEventPublisher, never())
                    .publishMessageCreatedEvent(any());
        }

        @Test
        @DisplayName("should publish event for IMAGE message type")
        void sendMessage_imageMessage_publishesCorrectEventType() {
            // Arrange
            ChatMessageRequestDTO imageRequest = ChatMessageRequestDTO.builder()
                    .senderId("user1")
                    .recipientId("user2")
                    .chatRoomId("user1-user2")
                    .messageType(MessageType.IMAGE)
                    .mediaUrl("https://s3.amazonaws.com/bucket/photo.jpg")
                    .build();

            ChatMessageResponseDTO imageResponse = ChatMessageResponseDTO.builder()
                    .messageId("456")
                    .senderId("user1")
                    .recipientId("user2")
                    .chatRoomId("user1-user2")
                    .messageType(MessageType.IMAGE)
                    .mediaUrl("https://s3.amazonaws.com/bucket/photo.jpg")
                    .status("SENT")
                    .timestamp(Instant.now())
                    .build();

            when(messageService.saveMessage(imageRequest)).thenReturn(imageResponse);
            stubPublisherSuccess();

            // Act
            controller.sendMessage(imageRequest, principal);

            // Assert
            ArgumentCaptor<MessageCreatedEvent> eventCaptor =
                    ArgumentCaptor.forClass(MessageCreatedEvent.class);
            verify(messageEventPublisher).publishMessageCreatedEvent(eventCaptor.capture());

            assertThat(eventCaptor.getValue().getType()).isEqualTo(MessageType.IMAGE);
            assertThat(eventCaptor.getValue().getMessageId()).isEqualTo("456");
        }

        @Test
        @DisplayName("should publish event with correct chatRoomId as partition key")
        void sendMessage_correctChatRoomId() {
            // Arrange
            when(messageService.saveMessage(requestDTO)).thenReturn(responseDTO);
            stubPublisherSuccess();

            // Act
            controller.sendMessage(requestDTO, principal);

            // Assert
            ArgumentCaptor<MessageCreatedEvent> eventCaptor =
                    ArgumentCaptor.forClass(MessageCreatedEvent.class);
            verify(messageEventPublisher).publishMessageCreatedEvent(eventCaptor.capture());

            assertThat(eventCaptor.getValue().getChatRoomId()).isEqualTo("user1-user2");
        }
    }

    private <T> CompletableFuture<T> failedFuture(Exception ex) {
        CompletableFuture<T> future = new CompletableFuture<>();
        future.completeExceptionally(ex);
        return future;
    }
}
