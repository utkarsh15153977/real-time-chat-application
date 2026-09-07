package com.chatApplication.message_service.repository;

import com.chatApplication.message_service.entity.Message;
import com.chatApplication.message_service.entity.MessageStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class MessageRepositoryTest {

    @Autowired
    private MessageRepository messageRepository;

    @BeforeEach
    void setUp() {
        messageRepository.deleteAll();

        messageRepository.save(Message.builder()
                .senderId("user1")
                .receiverId("user2")
                .content("Hello from user1")
                .status(MessageStatus.SENT)
                .timestamp(LocalDateTime.of(2025, 1, 15, 10, 0))
                .build());

        messageRepository.save(Message.builder()
                .senderId("user2")
                .receiverId("user1")
                .content("Hello from user2")
                .status(MessageStatus.DELIVERED)
                .timestamp(LocalDateTime.of(2025, 1, 15, 10, 5))
                .build());

        messageRepository.save(Message.builder()
                .senderId("user1")
                .receiverId("user2")
                .content("Follow up from user1")
                .status(MessageStatus.READ)
                .timestamp(LocalDateTime.of(2025, 1, 15, 10, 10))
                .build());

        messageRepository.save(Message.builder()
                .senderId("user3")
                .receiverId("user4")
                .content("Unrelated message")
                .status(MessageStatus.SENT)
                .timestamp(LocalDateTime.of(2025, 1, 15, 11, 0))
                .build());
    }

    @Test
    @DisplayName("should find conversation between two users")
    void findConversation_validUsers_returnsMessages() {
        List<Message> messages = messageRepository.findConversation("user1", "user2");

        assertThat(messages).hasSize(3);
        assertThat(messages.get(0).getContent()).isEqualTo("Hello from user1");
    }

    @Test
    @DisplayName("should find unread messages")
    void findUnreadMessages_validUsers_returnsUnread() {
        List<Message> unread = messageRepository.findUnreadMessages("user1", "user2");

        assertThat(unread).hasSize(1);
        assertThat(unread.get(0).getContent()).isEqualTo("Hello from user1");
    }

    @Test
    @DisplayName("should count unread messages")
    void countUnreadMessages_validUsers_returnsCount() {
        Long count = messageRepository.countUnreadMessages("user1", "user2");

        assertThat(count).isEqualTo(1L);
    }

    @Test
    @DisplayName("should find recent messages for user")
    void findRecentMessages_validUser_returnsMessages() {
        List<Message> recent = messageRepository.findRecentMessages("user1");

        assertThat(recent).hasSize(3);
    }

    @Test
    @DisplayName("should find messages by sender and receiver")
    void findBySenderIdAndReceiverId_validPair_returnsMessages() {
        List<Message> messages = messageRepository.findBySenderIdAndReceiverId("user1", "user2");

        assertThat(messages).hasSize(2);
    }

    @Test
    @DisplayName("should return empty for non-existent conversation")
    void findConversation_noMessages_returnsEmpty() {
        List<Message> messages = messageRepository.findConversation("user5", "user6");

        assertThat(messages).isEmpty();
    }
}
