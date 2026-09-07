package com.chatApplication.message_service.service;

import com.chatApplication.message_service.dto.ChatMessageResponseDTO;
import com.chatApplication.message_service.dto.InboxItemDTO;
import com.chatApplication.message_service.dto.TotalUnreadCountDTO;
import com.chatApplication.message_service.entity.Message;
import com.chatApplication.message_service.entity.MessageStatus;
import com.chatApplication.message_service.entity.MessageType;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for InboxServiceImpl.
 * <p>
 * Tests:
 * - Inbox overview with mixed read/unread messages across multiple chat rooms
 * - Total unread count accuracy
 * - Pagination sorting by latest message timestamp
 * - Inbox update notifications via STOMP
 */
@ExtendWith(MockitoExtension.class)
class InboxServiceTest {

    @Mock
    private MessageRepository messageRepository;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private InboxServiceImpl inboxService;

    private Message messageFromUser1;
    private Message messageFromUser3;
    private Message lastMessageBetweenUser1AndUser2;

    @BeforeEach
    void setUp() {
        messageFromUser1 = Message.builder()
                .msgId(1L)
                .senderId("user1")
                .receiverId("user2")
                .content("Hello from user1")
                .status(MessageStatus.SENT)
                .messageType(MessageType.TEXT)
                .timestamp(LocalDateTime.of(2025, 1, 15, 10, 30))
                .isAttachment(false)
                .build();

        messageFromUser3 = Message.builder()
                .msgId(2L)
                .senderId("user3")
                .receiverId("user2")
                .content("Hello from user3")
                .status(MessageStatus.READ)
                .messageType(MessageType.TEXT)
                .timestamp(LocalDateTime.of(2025, 1, 15, 11, 0))
                .isAttachment(false)
                .build();

        lastMessageBetweenUser1AndUser2 = Message.builder()
                .msgId(3L)
                .senderId("user2")
                .receiverId("user1")
                .content("Reply from user2")
                .status(MessageStatus.SENT)
                .messageType(MessageType.TEXT)
                .timestamp(LocalDateTime.of(2025, 1, 15, 12, 0))
                .isAttachment(false)
                .build();
    }

    // ================================================================
    // getInboxOverview tests
    // ================================================================

    @Nested
    @DisplayName("getInboxOverview")
    class GetInboxOverviewTests {

        @Test
        @DisplayName("should return inbox with multiple conversations")
        void getInboxOverview_multipleConversations_returnsPage() {
            // Arrange
            Pageable pageable = PageRequest.of(0, 20,
                    Sort.by(Sort.Direction.DESC, "lastMessageTimestamp"));

            when(messageRepository.findLatestMessagesPerConversation("user2"))
                    .thenReturn(List.of(messageFromUser1, messageFromUser3));

            when(messageRepository.countUnreadInConversation("user2", "user1"))
                    .thenReturn(3L);
            when(messageRepository.countUnreadInConversation("user2", "user3"))
                    .thenReturn(0L);

            // Act
            Page<InboxItemDTO> result = inboxService.getInboxOverview("user2", pageable);

            // Assert
            assertThat(result).isNotNull();
            assertThat(result.getContent()).hasSize(2);
            assertThat(result.getTotalElements()).isEqualTo(2);

            // Verify sorting (newest first)
            InboxItemDTO firstItem = result.getContent().get(0);
            assertThat(firstItem.getRecipientId()).isEqualTo("user3");
            assertThat(firstItem.getUnreadCount()).isEqualTo(0);

            InboxItemDTO secondItem = result.getContent().get(1);
            assertThat(secondItem.getRecipientId()).isEqualTo("user1");
            assertThat(secondItem.getUnreadCount()).isEqualTo(3);

            verify(messageRepository).findLatestMessagesPerConversation("user2");
            verify(messageRepository).countUnreadInConversation("user2", "user1");
            verify(messageRepository).countUnreadInConversation("user2", "user3");
        }

        @Test
        @DisplayName("should return empty page when no conversations")
        void getInboxOverview_noConversations_returnsEmptyPage() {
            // Arrange
            Pageable pageable = PageRequest.of(0, 20);

            when(messageRepository.findLatestMessagesPerConversation("user2"))
                    .thenReturn(List.of());

            // Act
            Page<InboxItemDTO> result = inboxService.getInboxOverview("user2", pageable);

            // Assert
            assertThat(result).isNotNull();
            assertThat(result.getContent()).isEmpty();
            assertThat(result.getTotalElements()).isEqualTo(0);
        }

        @Test
        @DisplayName("should handle single conversation correctly")
        void getInboxOverview_singleConversation_returnsSingleItem() {
            // Arrange
            Pageable pageable = PageRequest.of(0, 20);

            when(messageRepository.findLatestMessagesPerConversation("user2"))
                    .thenReturn(List.of(messageFromUser1));

            when(messageRepository.countUnreadInConversation("user2", "user1"))
                    .thenReturn(5L);

            // Act
            Page<InboxItemDTO> result = inboxService.getInboxOverview("user2", pageable);

            // Assert
            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getRecipientId()).isEqualTo("user1");
            assertThat(result.getContent().get(0).getUnreadCount()).isEqualTo(5);
            assertThat(result.getContent().get(0).getChatRoomId()).isEqualTo("user1-user2");
        }

        @Test
        @DisplayName("should generate consistent chat room IDs")
        void getInboxOverview_generatesConsistentChatRoomIds() {
            // Arrange
            Pageable pageable = PageRequest.of(0, 20);

            Message messageFromUser2 = Message.builder()
                    .msgId(4L)
                    .senderId("user2")
                    .receiverId("user1")
                    .content("Message from user2")
                    .status(MessageStatus.SENT)
                    .messageType(MessageType.TEXT)
                    .timestamp(LocalDateTime.of(2025, 1, 15, 13, 0))
                    .build();

            when(messageRepository.findLatestMessagesPerConversation("user1"))
                    .thenReturn(List.of(messageFromUser2));

            when(messageRepository.countUnreadInConversation("user1", "user2"))
                    .thenReturn(1L);

            // Act
            Page<InboxItemDTO> result = inboxService.getInboxOverview("user1", pageable);

            // Assert - chat room ID should be "user1-user2" (smaller first)
            assertThat(result.getContent().get(0).getChatRoomId()).isEqualTo("user1-user2");
        }

        @Test
        @DisplayName("should apply pagination correctly")
        void getInboxOverview_withPagination_appliesCorrectly() {
            // Arrange
            Pageable pageable = PageRequest.of(0, 1); // Only first item

            // Create multiple messages for different conversations
            Message message2 = Message.builder()
                    .msgId(2L)
                    .senderId("user3")
                    .receiverId("user2")
                    .content("Message from user3")
                    .status(MessageStatus.SENT)
                    .messageType(MessageType.TEXT)
                    .timestamp(LocalDateTime.of(2025, 1, 15, 11, 0))
                    .build();

            when(messageRepository.findLatestMessagesPerConversation("user2"))
                    .thenReturn(List.of(messageFromUser1, message2));

            when(messageRepository.countUnreadInConversation("user2", "user1"))
                    .thenReturn(1L);
            when(messageRepository.countUnreadInConversation("user2", "user3"))
                    .thenReturn(2L);

            // Act
            Page<InboxItemDTO> result = inboxService.getInboxOverview("user2", pageable);

            // Assert
            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getTotalElements()).isEqualTo(2);
            assertThat(result.getTotalPages()).isEqualTo(2);
        }
    }

    // ================================================================
    // getTotalUnreadCount tests
    // ================================================================

    @Nested
    @DisplayName("getTotalUnreadCount")
    class GetTotalUnreadCountTests {

        @Test
        @DisplayName("should return total unread count")
        void getTotalUnreadCount_withUnread_returnsCount() {
            // Arrange
            when(messageRepository.countTotalUnreadMessages("user2"))
                    .thenReturn(10L);

            // Act
            TotalUnreadCountDTO result = inboxService.getTotalUnreadCount("user2");

            // Assert
            assertThat(result).isNotNull();
            assertThat(result.getTotalUnreadCount()).isEqualTo(10L);
            verify(messageRepository).countTotalUnreadMessages("user2");
        }

        @Test
        @DisplayName("should return zero when no unread messages")
        void getTotalUnreadCount_noUnread_returnsZero() {
            // Arrange
            when(messageRepository.countTotalUnreadMessages("user2"))
                    .thenReturn(0L);

            // Act
            TotalUnreadCountDTO result = inboxService.getTotalUnreadCount("user2");

            // Assert
            assertThat(result).isNotNull();
            assertThat(result.getTotalUnreadCount()).isEqualTo(0L);
        }

        @Test
        @DisplayName("should return correct count for large numbers")
        void getTotalUnreadCount_largeNumber_returnsCorrectCount() {
            // Arrange
            when(messageRepository.countTotalUnreadMessages("user2"))
                    .thenReturn(999999L);

            // Act
            TotalUnreadCountDTO result = inboxService.getTotalUnreadCount("user2");

            // Assert
            assertThat(result.getTotalUnreadCount()).isEqualTo(999999L);
        }
    }

    // ================================================================
    // notifyInboxUpdate tests
    // ================================================================

    @Nested
    @DisplayName("notifyInboxUpdate")
    class NotifyInboxUpdateTests {

        @Test
        @DisplayName("should broadcast inbox update to recipient")
        void notifyInboxUpdate_validMessage_broadcastsUpdate() {
            // Arrange
            ChatMessageResponseDTO responseDTO = ChatMessageResponseDTO.builder()
                    .messageId("1")
                    .senderId("user1")
                    .recipientId("user2")
                    .chatRoomId("user1-user2")
                    .content("Hello from user1")
                    .messageType(MessageType.TEXT)
                    .timestamp(Instant.now())
                    .build();

            when(messageRepository.countUnreadInConversation("user2", "user1"))
                    .thenReturn(3L);

            // Act
            inboxService.notifyInboxUpdate(responseDTO);

            // Assert
            verify(messageRepository).countUnreadInConversation("user2", "user1");
            verify(messagingTemplate).convertAndSendToUser(
                    eq("user2"),
                    eq("/queue/inbox"),
                    any(InboxItemDTO.class));
        }

        @Test
        @DisplayName("should handle repository exception gracefully")
        void notifyInboxUpdate_repositoryException_handlesGracefully() {
            // Arrange
            ChatMessageResponseDTO responseDTO = ChatMessageResponseDTO.builder()
                    .messageId("1")
                    .senderId("user1")
                    .recipientId("user2")
                    .chatRoomId("user1-user2")
                    .content("Hello")
                    .messageType(MessageType.TEXT)
                    .timestamp(Instant.now())
                    .build();

            when(messageRepository.countUnreadInConversation("user2", "user1"))
                    .thenThrow(new RuntimeException("Database error"));

            // Act & Assert - should not throw exception
            assertThatCode(() -> inboxService.notifyInboxUpdate(responseDTO))
                    .doesNotThrowAnyException();

            // Verify messagingTemplate was NOT called due to exception
            verify(messagingTemplate, never()).convertAndSendToUser(
                    anyString(), anyString(), any());
        }

        @Test
        @DisplayName("should include correct unread count in broadcast")
        void notifyInboxUpdate_includesCorrectUnreadCount() {
            // Arrange
            ChatMessageResponseDTO responseDTO = ChatMessageResponseDTO.builder()
                    .messageId("1")
                    .senderId("user1")
                    .recipientId("user2")
                    .chatRoomId("user1-user2")
                    .content("Hello")
                    .messageType(MessageType.TEXT)
                    .timestamp(Instant.now())
                    .build();

            when(messageRepository.countUnreadInConversation("user2", "user1"))
                    .thenReturn(7L);

            // Act
            inboxService.notifyInboxUpdate(responseDTO);

            // Assert - verify the broadcast DTO has correct unread count
            verify(messagingTemplate).convertAndSendToUser(
                    eq("user2"),
                    eq("/queue/inbox"),
                    argThat(inboxItem -> {
                        InboxItemDTO item = (InboxItemDTO) inboxItem;
                        return item.getUnreadCount() == 7L
                                && item.getRecipientId().equals("user1")
                                && item.getChatRoomId().equals("user1-user2");
                    }));
        }
    }
}
