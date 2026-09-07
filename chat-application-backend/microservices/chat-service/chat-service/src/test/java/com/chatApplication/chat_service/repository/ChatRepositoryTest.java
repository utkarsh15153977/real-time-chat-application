package com.chatApplication.chat_service.repository;

import com.chatApplication.chat_service.entity.Chat;
import com.chatApplication.chat_service.entity.ChatMember;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
class ChatRepositoryTest {

    @Autowired
    private ChatRepository chatRepository;

    @Autowired
    private ChatMemberRepository chatMemberRepository;

    private Chat privateChat1;
    private Chat privateChat2;
    private Chat groupChat;

    @BeforeEach
    void setUp() {
        chatMemberRepository.deleteAll();
        chatRepository.deleteAll();

        privateChat1 = chatRepository.save(Chat.builder()
                .name(null)
                .isGroup(false)
                .groupIcon(null)
                .createdBy(10L)
                .active(true)
                .build());

        privateChat2 = chatRepository.save(Chat.builder()
                .name(null)
                .isGroup(false)
                .groupIcon(null)
                .createdBy(30L)
                .active(true)
                .build());

        groupChat = chatRepository.save(Chat.builder()
                .name("Test Group")
                .isGroup(true)
                .groupIcon("icon.png")
                .createdBy(10L)
                .active(true)
                .build());

        chatMemberRepository.save(ChatMember.builder()
                .chatId(privateChat1.getId())
                .userId(10L)
                .admin(false)
                .build());

        chatMemberRepository.save(ChatMember.builder()
                .chatId(privateChat1.getId())
                .userId(20L)
                .admin(false)
                .build());

        chatMemberRepository.save(ChatMember.builder()
                .chatId(privateChat2.getId())
                .userId(30L)
                .admin(false)
                .build());

        chatMemberRepository.save(ChatMember.builder()
                .chatId(privateChat2.getId())
                .userId(40L)
                .admin(false)
                .build());

        chatMemberRepository.save(ChatMember.builder()
                .chatId(groupChat.getId())
                .userId(10L)
                .admin(true)
                .build());

        chatMemberRepository.save(ChatMember.builder()
                .chatId(groupChat.getId())
                .userId(20L)
                .admin(false)
                .build());
    }

    @Test
    @DisplayName("should find private chat between two users")
    void findPrivateChatBetween_existingChat_returnsChat() {
        Optional<Chat> result = chatRepository.findPrivateChatBetween(10L, 20L);

        assertTrue(result.isPresent());
        assertEquals(privateChat1.getId(), result.get().getId());
        assertFalse(result.get().getIsGroup());
    }

    @Test
    @DisplayName("should return empty when no private chat exists")
    void findPrivateChatBetween_noChat_returnsEmpty() {
        Optional<Chat> result = chatRepository.findPrivateChatBetween(10L, 99L);

        assertFalse(result.isPresent());
    }

    @Test
    @DisplayName("should not return group chat as private chat")
    void findPrivateChatBetween_groupChat_returnsEmpty() {
        Optional<Chat> result = chatRepository.findPrivateChatBetween(10L, 20L);

        assertTrue(result.isPresent());
        assertNotEquals(groupChat.getId(), result.get().getId());
    }

    @Test
    @DisplayName("should find chat by ID")
    void findById_existingChat_returnsChat() {
        Optional<Chat> result = chatRepository.findById(privateChat1.getId());

        assertTrue(result.isPresent());
        assertFalse(result.get().getIsGroup());
    }

    @Test
    @DisplayName("should save chat with active default")
    void saveChat_setsActiveTrue() {
        Chat newChat = chatRepository.save(Chat.builder()
                .name("New Chat")
                .isGroup(true)
                .createdBy(50L)
                .build());

        assertTrue(newChat.getActive());
        assertNotNull(newChat.getCreatedAt());
        assertNotNull(newChat.getUpdatedAt());
    }

    @Test
    @DisplayName("should enforce unique constraint on chat_members")
    void saveChatMember_duplicateConstraint_throwsException() {
        assertThrows(Exception.class, () -> {
            chatMemberRepository.save(ChatMember.builder()
                    .chatId(privateChat1.getId())
                    .userId(10L)
                    .admin(false)
                    .build());
            chatMemberRepository.flush();
        });
    }
}
