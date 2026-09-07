package com.chatApplication.message_service.service;

import com.chatApplication.message_service.dto.*;
import com.chatApplication.message_service.entity.Message;
import com.chatApplication.message_service.entity.MessageStatus;
import com.chatApplication.message_service.entity.MessageType;
import com.chatApplication.message_service.exception.MessageValidationException;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MessageServiceImplTest {

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

    private Message message;

    @BeforeEach
    void setUp() {
        message = Message.builder()
                .msgId(1L)
                .senderId("user1")
                .receiverId("user2")
                .content("Hello World")
                .status(MessageStatus.SENT)
                .messageType(MessageType.TEXT)
                .timestamp(LocalDateTime.of(2025, 1, 15, 10, 30))
                .isAttachment(false)
                .build();
    }

    // ================================================================
    // saveMessage tests (NEW - Media Messaging)
    // ================================================================

    @Nested
    @DisplayName("saveMessage")
    class SaveMessageTests {

        @Test
        @DisplayName("should persist a TEXT message with default type")
        void saveMessage_textMessage_savesAndReturns() {
            ChatMessageRequestDTO request = ChatMessageRequestDTO.builder()
                    .senderId("user1")
                    .recipientId("user2")
                    .chatRoomId("user1-user2")
                    .content("Hello World")
                    .messageType(MessageType.TEXT)
                    .build();

            when(messageRepository.save(any(Message.class))).thenReturn(message);

            ChatMessageResponseDTO response = messageService.saveMessage(request);

            assertThat(response).isNotNull();
            assertThat(response.getMessageId()).isEqualTo("1");
            assertThat(response.getSenderId()).isEqualTo("user1");
            assertThat(response.getRecipientId()).isEqualTo("user2");
            assertThat(response.getContent()).isEqualTo("Hello World");
            assertThat(response.getMessageType()).isEqualTo(MessageType.TEXT);

            verify(messageRepository).save(any(Message.class));
        }

        @Test
        @DisplayName("should default messageType to TEXT when null")
        void saveMessage_nullType_defaultsToText() {
            ChatMessageRequestDTO request = ChatMessageRequestDTO.builder()
                    .senderId("user1")
                    .recipientId("user2")
                    .content("Hello")
                    .messageType(null)
                    .build();

            when(messageRepository.save(any(Message.class))).thenReturn(message);

            ChatMessageResponseDTO response = messageService.saveMessage(request);

            assertThat(response.getMessageType()).isEqualTo(MessageType.TEXT);
        }

        @Test
        @DisplayName("should persist an IMAGE message with media fields")
        void saveMessage_imageMessage_savesMediaFields() {
            Message imageMessage = Message.builder()
                    .msgId(2L)
                    .senderId("user1")
                    .receiverId("user2")
                    .content(null)
                    .messageType(MessageType.IMAGE)
                    .mediaUrl("https://s3.amazonaws.com/bucket/chats/room1/photo.jpg")
                    .fileKey("chats/room1/uuid-photo.jpg")
                    .fileSizeBytes(2048000L)
                    .status(MessageStatus.SENT)
                    .timestamp(LocalDateTime.now())
                    .build();

            ChatMessageRequestDTO request = ChatMessageRequestDTO.builder()
                    .senderId("user1")
                    .recipientId("user2")
                    .chatRoomId("user1-user2")
                    .messageType(MessageType.IMAGE)
                    .mediaUrl("https://s3.amazonaws.com/bucket/chats/room1/photo.jpg")
                    .fileKey("chats/room1/uuid-photo.jpg")
                    .fileSizeBytes(2048000L)
                    .build();

            when(messageRepository.save(any(Message.class))).thenReturn(imageMessage);

            ChatMessageResponseDTO response = messageService.saveMessage(request);

            assertThat(response.getMessageType()).isEqualTo(MessageType.IMAGE);
            assertThat(response.getMediaUrl()).contains("photo.jpg");
            assertThat(response.getFileKey()).contains("uuid-photo.jpg");
            assertThat(response.getFileSizeBytes()).isEqualTo(2048000L);

            verify(messageRepository).save(any(Message.class));
        }

        @Test
        @DisplayName("should persist a FILE message with all media fields")
        void saveMessage_fileMessage_savesAllFields() {
            Message fileMessage = Message.builder()
                    .msgId(3L)
                    .senderId("user1")
                    .receiverId("user2")
                    .messageType(MessageType.FILE)
                    .mediaUrl("https://s3.amazonaws.com/bucket/chats/room1/doc.pdf")
                    .fileKey("chats/room1/uuid-doc.pdf")
                    .fileSizeBytes(1048576L)
                    .status(MessageStatus.SENT)
                    .timestamp(LocalDateTime.now())
                    .build();

            ChatMessageRequestDTO request = ChatMessageRequestDTO.builder()
                    .senderId("user1")
                    .recipientId("user2")
                    .chatRoomId("user1-user2")
                    .messageType(MessageType.FILE)
                    .mediaUrl("https://s3.amazonaws.com/bucket/chats/room1/doc.pdf")
                    .fileKey("chats/room1/uuid-doc.pdf")
                    .fileSizeBytes(1048576L)
                    .build();

            when(messageRepository.save(any(Message.class))).thenReturn(fileMessage);

            ChatMessageResponseDTO response = messageService.saveMessage(request);

            assertThat(response.getMessageType()).isEqualTo(MessageType.FILE);
            assertThat(response.getFileSizeBytes()).isEqualTo(1048576L);
        }

        @Test
        @DisplayName("should throw when IMAGE message has no mediaUrl")
        void saveMessage_imageNoMediaUrl_throwsException() {
            ChatMessageRequestDTO request = ChatMessageRequestDTO.builder()
                    .senderId("user1")
                    .recipientId("user2")
                    .messageType(MessageType.IMAGE)
                    .fileKey("chats/room1/uuid-photo.jpg")
                    .build();

            assertThatThrownBy(() -> messageService.saveMessage(request))
                    .isInstanceOf(MessageValidationException.class)
                    .hasMessageContaining("mediaUrl is required");
        }

        @Test
        @DisplayName("should throw when VIDEO message has no fileKey")
        void saveMessage_videoNoFileKey_throwsException() {
            ChatMessageRequestDTO request = ChatMessageRequestDTO.builder()
                    .senderId("user1")
                    .recipientId("user2")
                    .messageType(MessageType.VIDEO)
                    .mediaUrl("https://s3.amazonaws.com/bucket/video.mp4")
                    .build();

            assertThatThrownBy(() -> messageService.saveMessage(request))
                    .isInstanceOf(MessageValidationException.class)
                    .hasMessageContaining("fileKey is required");
        }

        @Test
        @DisplayName("should throw when TEXT message has no content")
        void saveMessage_textNoContent_throwsException() {
            ChatMessageRequestDTO request = ChatMessageRequestDTO.builder()
                    .senderId("user1")
                    .recipientId("user2")
                    .messageType(MessageType.TEXT)
                    .build();

            assertThatThrownBy(() -> messageService.saveMessage(request))
                    .isInstanceOf(MessageValidationException.class)
                    .hasMessageContaining("content is required");
        }
    }

    // ================================================================
    // getChatHistory tests (NEW - Media Messaging)
    // ================================================================

    @Nested
    @DisplayName("getChatHistory")
    class GetChatHistoryTests {

        @Test
        @DisplayName("should return paginated messages for a chat room")
        void getChatHistory_validRoom_returnsPage() {
            Pageable pageable = PageRequest.of(0, 20);
            Page<Message> page = new PageImpl<>(List.of(message), pageable, 1);

            when(messageRepository.findConversationPaged(
                    eq("user1"), eq("user2"), eq(pageable)))
                    .thenReturn(page);

            Page<ChatMessageResponseDTO> result =
                    messageService.getChatHistory("user1-user2", pageable);

            assertThat(result).isNotNull();
            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getSenderId()).isEqualTo("user1");
        }

        @Test
        @DisplayName("should throw when chatRoomId is invalid")
        void getChatHistory_invalidRoom_throwsException() {
            Pageable pageable = PageRequest.of(0, 20);

            assertThatThrownBy(() ->
                    messageService.getChatHistory("invalid", pageable))
                    .isInstanceOf(MessageValidationException.class)
                    .hasMessageContaining("Invalid chatRoomId format");
        }
    }

    // ================================================================
    // Existing tests (preserved)
    // ================================================================

    @Nested
    @DisplayName("sendMessage")
    class SendMessageTests {

        @Test
        @DisplayName("should save message and send via WebSocket and Kafka")
        void sendMessage_validRequest_savesAndSends() {
            MessageRequest request = new MessageRequest();
            request.setSenderId("user1");
            request.setReceiverId("user2");
            request.setMessage("Hello World");

            when(messageRepository.save(any(Message.class))).thenReturn(message);

            MessageResponse response = messageService.sendMessage(request);

            assertThat(response).isNotNull();
            assertThat(response.getMsgId()).isEqualTo(1L);
            assertThat(response.getSenderId()).isEqualTo("user1");
            assertThat(response.getContent()).isEqualTo("Hello World");

            verify(messageRepository).save(any(Message.class));
            verify(messagingTemplate).convertAndSendToUser(
                    eq("user2"),
                    eq("/queue/messages"),
                    any(ChatMessage.class));
            verify(messageProducer).publish(any());
        }
    }

    @Nested
    @DisplayName("getConversation")
    class GetConversationTests {

        @Test
        @DisplayName("should return conversation between two users")
        void getConversation_validUsers_returnsMessages() {
            when(messageRepository.findConversation("user1", "user2"))
                    .thenReturn(List.of(message));

            List<MessageResponse> responses = messageService.getConversation("user1", "user2");

            assertThat(responses).hasSize(1);
            assertThat(responses.get(0).getSenderId()).isEqualTo("user1");
        }
    }

    @Nested
    @DisplayName("getMessage")
    class GetMessageTests {

        @Test
        @DisplayName("should return message by id")
        void getMessage_validId_returnsMessage() {
            when(messageRepository.findById(1L)).thenReturn(Optional.of(message));

            MessageResponse response = messageService.getMessage(1L);

            assertThat(response).isNotNull();
            assertThat(response.getMsgId()).isEqualTo(1L);
        }

        @Test
        @DisplayName("should throw RuntimeException when message not found")
        void getMessage_invalidId_throwsException() {
            when(messageRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> messageService.getMessage(99L))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Message not found");
        }
    }

    @Nested
    @DisplayName("markAsDelivered")
    class MarkAsDeliveredTests {

        @Test
        @DisplayName("should mark SENT message as DELIVERED")
        void markAsDelivered_sentMessage_returnsDelivered() {
            when(messageRepository.findById(1L)).thenReturn(Optional.of(message));
            when(messageRepository.save(any(Message.class))).thenReturn(message);

            MessageResponse response = messageService.markAsDelivered(1L);

            assertThat(response).isNotNull();
            verify(messagingTemplate).convertAndSendToUser(
                    eq("user1"),
                    eq("/queue/receipts"),
                    any(ReadReceiptEvent.class));
        }

        @Test
        @DisplayName("should throw RuntimeException when message not found")
        void markAsDelivered_invalidId_throwsException() {
            when(messageRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> messageService.markAsDelivered(99L))
                    .isInstanceOf(RuntimeException.class);
        }
    }

    @Nested
    @DisplayName("markAsSeen")
    class MarkAsSeenTests {

        @Test
        @DisplayName("should mark message as READ")
        void markAsSeen_validMessage_returnsRead() {
            when(messageRepository.findById(1L)).thenReturn(Optional.of(message));
            when(messageRepository.save(any(Message.class))).thenReturn(message);

            MessageResponse response = messageService.markAsSeen(1L);

            assertThat(response).isNotNull();
            verify(messagingTemplate).convertAndSendToUser(
                    eq("user1"),
                    eq("/queue/receipts"),
                    any(ReadReceiptEvent.class));
        }

        @Test
        @DisplayName("should throw RuntimeException when message not found")
        void markAsSeen_invalidId_throwsException() {
            when(messageRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> messageService.markAsSeen(99L))
                    .isInstanceOf(RuntimeException.class);
        }
    }

    @Nested
    @DisplayName("markAllAsSeen")
    class MarkAllAsSeenTests {

        @Test
        @DisplayName("should mark all unread messages as read")
        void markAllAsSeen_validRequest_marksAll() {
            Message unreadMsg = Message.builder()
                    .msgId(2L)
                    .senderId("user1")
                    .receiverId("user2")
                    .status(MessageStatus.DELIVERED)
                    .build();

            when(messageRepository.findUnreadMessages("user1", "user2"))
                    .thenReturn(List.of(unreadMsg));

            messageService.markAllAsSeen("user1", "user2");

            verify(messageRepository).saveAll(any());
        }
    }

    @Nested
    @DisplayName("deleteMessage")
    class DeleteMessageTests {

        @Test
        @DisplayName("should delete message when it exists")
        void deleteMessage_existingMessage_deletes() {
            when(messageRepository.existsById(1L)).thenReturn(true);

            messageService.deleteMessage(1L);

            verify(messageRepository).deleteById(1L);
        }

        @Test
        @DisplayName("should throw RuntimeException when message not found")
        void deleteMessage_notFound_throwsException() {
            when(messageRepository.existsById(99L)).thenReturn(false);

            assertThatThrownBy(() -> messageService.deleteMessage(99L))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Message not found");
        }
    }

    @Nested
    @DisplayName("editMessage")
    class EditMessageTests {

        @Test
        @DisplayName("should edit message content")
        void editMessage_validRequest_updatesContent() {
            when(messageRepository.findById(1L)).thenReturn(Optional.of(message));
            when(messageRepository.save(any(Message.class))).thenReturn(message);

            MessageResponse response = messageService.editMessage(1L, "Edited content");

            assertThat(response).isNotNull();
            verify(messageRepository).save(any(Message.class));
        }

        @Test
        @DisplayName("should throw RuntimeException when message not found")
        void editMessage_notFound_throwsException() {
            when(messageRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> messageService.editMessage(99L, "content"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Message not found");
        }
    }

    @Nested
    @DisplayName("getUnreadCount")
    class GetUnreadCountTests {

        @Test
        @DisplayName("should return unread count")
        void getUnreadCount_validRequest_returnsCount() {
            when(messageRepository.countUnreadMessages("user1", "user2")).thenReturn(5L);

            Long count = messageService.getUnreadCount("user1", "user2");

            assertThat(count).isEqualTo(5L);
        }
    }

    @Nested
    @DisplayName("getRecentMessages")
    class GetRecentMessagesTests {

        @Test
        @DisplayName("should return recent messages for user")
        void getRecentMessages_validUser_returnsMessages() {
            when(messageRepository.findRecentMessages("user1"))
                    .thenReturn(List.of(message));

            List<MessageResponse> responses = messageService.getRecentMessages("user1");

            assertThat(responses).hasSize(1);
        }
    }

    @Nested
    @DisplayName("exists")
    class ExistsTests {

        @Test
        @DisplayName("should return true when message exists")
        void exists_existingMessage_returnsTrue() {
            when(messageRepository.existsById(1L)).thenReturn(true);

            boolean result = messageService.exists(1L);

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should return false when message does not exist")
        void exists_nonExistingMessage_returnsFalse() {
            when(messageRepository.existsById(99L)).thenReturn(false);

            boolean result = messageService.exists(99L);

            assertThat(result).isFalse();
        }
    }
}
