package com.chatApplication.message_service.kafka;

import com.chatApplication.message_service.entity.MessageType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MessageEventPublisherTest {

    @Mock
    private KafkaTemplate<String, MessageCreatedEvent> messageCreatedKafkaTemplate;

    @InjectMocks
    private MessageEventPublisher messageEventPublisher;

    private MessageCreatedEvent testEvent;

    @BeforeEach
    void setUp() {
        testEvent = MessageCreatedEvent.builder()
                .messageId("123")
                .chatRoomId("user1-user2")
                .senderId("user1")
                .recipientId("user2")
                .content("Hello World")
                .type(MessageType.TEXT)
                .createdAt(Instant.parse("2025-01-15T10:30:00Z"))
                .build();
    }

    @Nested
    @DisplayName("publishMessageCreatedEvent")
    class PublishEventTests {

        @Test
        @DisplayName("should send event to correct topic with chatRoomId as key")
        void publishEvent_sendsToCorrectTopicWithRoomKey() {
            // Arrange
            CompletableFuture<SendResult<String, MessageCreatedEvent>> future =
                    CompletableFuture.completedFuture(mock(SendResult.class));

            when(messageCreatedKafkaTemplate.send(
                    eq("chat.message-created"),
                    eq("user1-user2"),
                    eq(testEvent)))
                    .thenReturn(future);

            // Act
            messageEventPublisher.publishMessageCreatedEvent(testEvent);

            // Assert
            ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<MessageCreatedEvent> eventCaptor = ArgumentCaptor.forClass(MessageCreatedEvent.class);

            verify(messageCreatedKafkaTemplate).send(
                    topicCaptor.capture(),
                    keyCaptor.capture(),
                    eventCaptor.capture());

            assertThat(topicCaptor.getValue()).isEqualTo("chat.message-created");
            assertThat(keyCaptor.getValue()).isEqualTo("user1-user2");
            assertThat(eventCaptor.getValue().getMessageId()).isEqualTo("123");
            assertThat(eventCaptor.getValue().getSenderId()).isEqualTo("user1");
            assertThat(eventCaptor.getValue().getRecipientId()).isEqualTo("user2");
        }

        @Test
        @DisplayName("should return CompletableFuture from send")
        void publishEvent_returnsCompletableFuture() {
            // Arrange
            CompletableFuture<SendResult<String, MessageCreatedEvent>> future =
                    CompletableFuture.completedFuture(mock(SendResult.class));

            when(messageCreatedKafkaTemplate.send(
                    anyString(), anyString(), any(MessageCreatedEvent.class)))
                    .thenReturn(future);

            // Act
            CompletableFuture<SendResult<String, MessageCreatedEvent>> result =
                    messageEventPublisher.publishMessageCreatedEvent(testEvent);

            // Assert
            assertThat(result).isNotNull();
            assertThat(result).isDone();
        }

        @Test
        @DisplayName("should use chatRoomId as partition key for in-order delivery")
        void publishEvent_usesChatRoomIdAsPartitionKey() {
            // Arrange
            MessageCreatedEvent event = MessageCreatedEvent.builder()
                    .messageId("456")
                    .chatRoomId("user3-user4")
                    .senderId("user3")
                    .recipientId("user4")
                    .content("Test message")
                    .type(MessageType.IMAGE)
                    .createdAt(Instant.now())
                    .build();

            CompletableFuture<SendResult<String, MessageCreatedEvent>> future =
                    CompletableFuture.completedFuture(mock(SendResult.class));

            when(messageCreatedKafkaTemplate.send(
                    eq("chat.message-created"),
                    eq("user3-user4"),
                    eq(event)))
                    .thenReturn(future);

            // Act
            messageEventPublisher.publishMessageCreatedEvent(event);

            // Assert
            ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
            verify(messageCreatedKafkaTemplate).send(
                    anyString(),
                    keyCaptor.capture(),
                    any(MessageCreatedEvent.class));

            assertThat(keyCaptor.getValue()).isEqualTo("user3-user4");
        }

        @Test
        @DisplayName("should handle Kafka send failure gracefully")
        void publishEvent_kafkaSendFailure_handlesGracefully() {
            // Arrange
            CompletableFuture<SendResult<String, MessageCreatedEvent>> failedFuture =
                    new CompletableFuture<>();
            failedFuture.completeExceptionally(new RuntimeException("Kafka broker unavailable"));

            when(messageCreatedKafkaTemplate.send(
                    anyString(), anyString(), any(MessageCreatedEvent.class)))
                    .thenReturn(failedFuture);

            // Act - should not throw
            CompletableFuture<SendResult<String, MessageCreatedEvent>> result =
                    messageEventPublisher.publishMessageCreatedEvent(testEvent);

            // Assert
            assertThat(result).isNotNull();
            assertThat(result.isCompletedExceptionally()).isTrue();
        }

        @Test
        @DisplayName("should publish event with all fields populated")
        void publishEvent_allFieldsPopulated() {
            // Arrange
            CompletableFuture<SendResult<String, MessageCreatedEvent>> future =
                    CompletableFuture.completedFuture(mock(SendResult.class));

            when(messageCreatedKafkaTemplate.send(
                    anyString(), anyString(), any(MessageCreatedEvent.class)))
                    .thenReturn(future);

            // Act
            messageEventPublisher.publishMessageCreatedEvent(testEvent);

            // Assert
            ArgumentCaptor<MessageCreatedEvent> eventCaptor = ArgumentCaptor.forClass(MessageCreatedEvent.class);
            verify(messageCreatedKafkaTemplate).send(
                    anyString(), anyString(), eventCaptor.capture());

            MessageCreatedEvent captured = eventCaptor.getValue();
            assertThat(captured.getMessageId()).isEqualTo("123");
            assertThat(captured.getChatRoomId()).isEqualTo("user1-user2");
            assertThat(captured.getSenderId()).isEqualTo("user1");
            assertThat(captured.getRecipientId()).isEqualTo("user2");
            assertThat(captured.getContent()).isEqualTo("Hello World");
            assertThat(captured.getType()).isEqualTo(MessageType.TEXT);
            assertThat(captured.getCreatedAt()).isEqualTo(Instant.parse("2025-01-15T10:30:00Z"));
        }
    }
}
