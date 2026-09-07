package com.chatApplication.message_service.service;

import com.chatApplication.message_service.dto.BulkStatusUpdateDTO;
import com.chatApplication.message_service.dto.ChatMessageRequestDTO;
import com.chatApplication.message_service.dto.ChatMessageResponseDTO;
import com.chatApplication.message_service.dto.MessageStatusUpdateDTO;
import com.chatApplication.message_service.entity.Message;
import com.chatApplication.message_service.entity.MessageStatus;
import com.chatApplication.message_service.entity.MessageType;
import com.chatApplication.message_service.kafka.MessageProducer;
import com.chatApplication.message_service.repository.MessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for MessageServiceImpl's Read Receipts & Dynamic Delivery Status methods.
 * <p>
 * Tests the SENT -> DELIVERED -> READ state machine:
 * - Single message status transitions
 * - readAt timestamp generation
 * - Bulk update query logic
 * - Non-recipient state manipulation protection
 * - Idempotent operations
 */
@ExtendWith(MockitoExtension.class)
class MessageStatusServiceTest {

    @Mock
    private MessageRepository messageRepository;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private MessageProducer messageProducer;

    @Mock
    private RedisMessagePublisher redisMessagePublisher;

    @InjectMocks
    private MessageServiceImpl messageService;

    private Message sentMessage;
    private Message deliveredMessage;

    @BeforeEach
    void setUp() {
        sentMessage = Message.builder()
                .msgId(1L)
                .senderId("user1")
                .receiverId("user2")
                .content("Hello World")
                .status(MessageStatus.SENT)
                .messageType(MessageType.TEXT)
                .timestamp(LocalDateTime.of(2025, 1, 15, 10, 30))
                .isAttachment(false)
                .build();

        deliveredMessage = Message.builder()
                .msgId(2L)
                .senderId("user1")
                .receiverId("user2")
                .content("Delivered Message")
                .status(MessageStatus.DELIVERED)
                .messageType(MessageType.TEXT)
                .timestamp(LocalDateTime.of(2025, 1, 15, 11, 0))
                .isAttachment(false)
                .build();
    }

    // ================================================================
    // markAsDelivered tests
    // ================================================================

    @Nested
    @DisplayName("markAsDelivered(String, String)")
    class MarkAsDeliveredTests {

        @Test
        @DisplayName("should transition SENT message to DELIVERED")
        void markAsDelivered_sentMessage_returnsDelivered() {
            when(messageRepository.updateMessageStatus(
                    eq(1L), eq("user2"), eq(MessageStatus.DELIVERED), eq(null)))
                    .thenReturn(1);
            when(messageRepository.findById(1L)).thenReturn(Optional.of(sentMessage));

            MessageStatusUpdateDTO result = messageService.markAsDelivered("1", "user2");

            assertThat(result).isNotNull();
            assertThat(result.getStatus()).isEqualTo(MessageStatus.DELIVERED);
            assertThat(result.getMessageId()).isEqualTo("1");
            assertThat(result.getSenderId()).isEqualTo("user1");
            assertThat(result.getRecipientId()).isEqualTo("user2");
            assertThat(result.getChatRoomId()).isEqualTo("user1-user2");
            assertThat(result.getTimestamp()).isNotNull();
        }

        @Test
        @DisplayName("should return null when message not found")
        void markAsDelivered_messageNotFound_returnsNull() {
            when(messageRepository.updateMessageStatus(
                    eq(99L), eq("user2"), eq(MessageStatus.DELIVERED), eq(null)))
                    .thenReturn(0);

            MessageStatusUpdateDTO result = messageService.markAsDelivered("99", "user2");

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("should return null when recipientId does not match")
        void markAsDelivered_wrongRecipient_returnsNull() {
            when(messageRepository.updateMessageStatus(
                    eq(1L), eq("wrongUser"), eq(MessageStatus.DELIVERED), eq(null)))
                    .thenReturn(0);

            MessageStatusUpdateDTO result = messageService.markAsDelivered("1", "wrongUser");

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("should not transition READ message to DELIVERED (backwards)")
        void markAsDelivered_readMessage_returnsNull() {
            when(messageRepository.updateMessageStatus(
                    eq(3L), eq("user2"), eq(MessageStatus.DELIVERED), eq(null)))
                    .thenReturn(0);

            MessageStatusUpdateDTO result = messageService.markAsDelivered("3", "user2");

            // Should return null because the bulk update didn't affect any rows
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("should handle idempotent DELIVERED -> DELIVERED transition")
        void markAsDelivered_alreadyDelivered_returnsNull() {
            when(messageRepository.updateMessageStatus(
                    eq(2L), eq("user2"), eq(MessageStatus.DELIVERED), eq(null)))
                    .thenReturn(0);

            MessageStatusUpdateDTO result = messageService.markAsDelivered("2", "user2");

            // Already DELIVERED, no rows updated
            assertThat(result).isNull();
        }
    }

    // ================================================================
    // markAsRead tests
    // ================================================================

    @Nested
    @DisplayName("markAsRead(String, String)")
    class MarkAsReadTests {

        @Test
        @DisplayName("should transition SENT message to READ with readAt timestamp")
        void markAsRead_sentMessage_returnsReadWithTimestamp() {
            when(messageRepository.updateMessageStatus(
                    eq(1L), eq("user2"), eq(MessageStatus.READ), any(Instant.class)))
                    .thenReturn(1);
            when(messageRepository.findById(1L)).thenReturn(Optional.of(sentMessage));

            MessageStatusUpdateDTO result = messageService.markAsRead("1", "user2");

            assertThat(result).isNotNull();
            assertThat(result.getStatus()).isEqualTo(MessageStatus.READ);
            assertThat(result.getMessageId()).isEqualTo("1");
            assertThat(result.getSenderId()).isEqualTo("user1");
            assertThat(result.getRecipientId()).isEqualTo("user2");
            assertThat(result.getTimestamp()).isNotNull();
        }

        @Test
        @DisplayName("should transition DELIVERED message to READ with readAt timestamp")
        void markAsRead_deliveredMessage_returnsReadWithTimestamp() {
            when(messageRepository.updateMessageStatus(
                    eq(2L), eq("user2"), eq(MessageStatus.READ), any(Instant.class)))
                    .thenReturn(1);
            when(messageRepository.findById(2L)).thenReturn(Optional.of(deliveredMessage));

            MessageStatusUpdateDTO result = messageService.markAsRead("2", "user2");

            assertThat(result).isNotNull();
            assertThat(result.getStatus()).isEqualTo(MessageStatus.READ);
        }

        @Test
        @DisplayName("should return null when message not found")
        void markAsRead_messageNotFound_returnsNull() {
            when(messageRepository.updateMessageStatus(
                    eq(99L), eq("user2"), eq(MessageStatus.READ), any(Instant.class)))
                    .thenReturn(0);

            MessageStatusUpdateDTO result = messageService.markAsRead("99", "user2");

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("should return null when recipientId does not match")
        void markAsRead_wrongRecipient_returnsNull() {
            when(messageRepository.updateMessageStatus(
                    eq(1L), eq("wrongUser"), eq(MessageStatus.READ), any(Instant.class)))
                    .thenReturn(0);

            MessageStatusUpdateDTO result = messageService.markAsRead("1", "wrongUser");

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("should handle idempotent READ -> READ transition")
        void markAsRead_alreadyRead_returnsNull() {
            when(messageRepository.updateMessageStatus(
                    eq(4L), eq("user2"), eq(MessageStatus.READ), any(Instant.class)))
                    .thenReturn(0);

            MessageStatusUpdateDTO result = messageService.markAsRead("4", "user2");

            // Already READ, no rows updated
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("should set readAt timestamp to current time")
        void markAsRead_setsReadAtTimestamp() {
            Instant beforeUpdate = Instant.now();

            when(messageRepository.updateMessageStatus(
                    eq(1L), eq("user2"), eq(MessageStatus.READ), any(Instant.class)))
                    .thenReturn(1);
            when(messageRepository.findById(1L)).thenReturn(Optional.of(sentMessage));

            MessageStatusUpdateDTO result = messageService.markAsRead("1", "user2");

            assertThat(result).isNotNull();
            assertThat(result.getTimestamp()).isAfterOrEqualTo(beforeUpdate);
        }
    }

    // ================================================================
    // markChatRoomAsRead tests
    // ================================================================

    @Nested
    @DisplayName("markChatRoomAsRead(String, String)")
    class MarkChatRoomAsReadTests {

        @Test
        @DisplayName("should bulk update all unread messages in chat room")
        void markChatRoomAsRead_unreadMessages_returnsBulkUpdate() {
            Message unread1 = Message.builder()
                    .msgId(10L)
                    .senderId("user1")
                    .receiverId("user2")
                    .status(MessageStatus.SENT)
                    .build();

            Message unread2 = Message.builder()
                    .msgId(11L)
                    .senderId("user1")
                    .receiverId("user2")
                    .status(MessageStatus.DELIVERED)
                    .build();

            when(messageRepository.findUnreadMessagesInChatRoom("user1-user2", "user2"))
                    .thenReturn(List.of(unread1, unread2));
            when(messageRepository.markMessagesAsReadInChatRoom(
                    eq("user1-user2"), eq("user2"), eq(MessageStatus.READ), any(Instant.class)))
                    .thenReturn(2);

            BulkStatusUpdateDTO result = messageService.markChatRoomAsRead("user1-user2", "user2");

            assertThat(result).isNotNull();
            assertThat(result.getChatRoomId()).isEqualTo("user1-user2");
            assertThat(result.getReaderId()).isEqualTo("user2");
            assertThat(result.getMessageIds()).containsExactly("10", "11");
            assertThat(result.getStatus()).isEqualTo(MessageStatus.READ);
            assertThat(result.getTimestamp()).isNotNull();
        }

        @Test
        @DisplayName("should return empty list when no unread messages")
        void markChatRoomAsRead_noUnread_returnsEmptyList() {
            when(messageRepository.findUnreadMessagesInChatRoom("user1-user2", "user2"))
                    .thenReturn(List.of());

            BulkStatusUpdateDTO result = messageService.markChatRoomAsRead("user1-user2", "user2");

            assertThat(result).isNotNull();
            assertThat(result.getMessageIds()).isEmpty();
            assertThat(result.getStatus()).isEqualTo(MessageStatus.READ);
        }

        @Test
        @DisplayName("should not affect messages from other recipients")
        void markChatRoomAsRead_onlyUpdatesRecipientMessages() {
            Message otherUserMessage = Message.builder()
                    .msgId(20L)
                    .senderId("user3")
                    .receiverId("user2")
                    .status(MessageStatus.SENT)
                    .build();

            when(messageRepository.findUnreadMessagesInChatRoom("user3-user2", "user2"))
                    .thenReturn(List.of(otherUserMessage));
            when(messageRepository.markMessagesAsReadInChatRoom(
                    eq("user3-user2"), eq("user2"), eq(MessageStatus.READ), any(Instant.class)))
                    .thenReturn(1);

            BulkStatusUpdateDTO result = messageService.markChatRoomAsRead("user3-user2", "user2");

            assertThat(result).isNotNull();
            assertThat(result.getMessageIds()).containsExactly("20");
            assertThat(result.getReaderId()).isEqualTo("user2");
        }

        @Test
        @DisplayName("should handle large batch of unread messages")
        void markChatRoomAsRead_largeBatch_returnsAllIds() {
            List<Message> largeBatch = new java.util.ArrayList<>();
            for (int i = 0; i < 100; i++) {
                largeBatch.add(Message.builder()
                        .msgId((long) (100 + i))
                        .senderId("user1")
                        .receiverId("user2")
                        .status(MessageStatus.SENT)
                        .build());
            }

            when(messageRepository.findUnreadMessagesInChatRoom("user1-user2", "user2"))
                    .thenReturn(largeBatch);
            when(messageRepository.markMessagesAsReadInChatRoom(
                    eq("user1-user2"), eq("user2"), eq(MessageStatus.READ), any(Instant.class)))
                    .thenReturn(100);

            BulkStatusUpdateDTO result = messageService.markChatRoomAsRead("user1-user2", "user2");

            assertThat(result).isNotNull();
            assertThat(result.getMessageIds()).hasSize(100);
        }
    }

    // ================================================================
    // saveMessage tests (verify SENT status on new messages)
    // ================================================================

    @Nested
    @DisplayName("saveMessage status verification")
    class SaveMessageStatusTests {

        @Test
        @DisplayName("should persist new message with SENT status")
        void saveMessage_newMessage_hasSentStatus() {
            ChatMessageRequestDTO request = ChatMessageRequestDTO.builder()
                    .senderId("user1")
                    .recipientId("user2")
                    .chatRoomId("user1-user2")
                    .content("Hello World")
                    .messageType(MessageType.TEXT)
                    .build();

            when(messageRepository.save(any(Message.class))).thenReturn(sentMessage);

            ChatMessageResponseDTO response = messageService.saveMessage(request);

            assertThat(response).isNotNull();
            assertThat(response.getStatus()).isEqualTo("SENT");
        }
    }
}
