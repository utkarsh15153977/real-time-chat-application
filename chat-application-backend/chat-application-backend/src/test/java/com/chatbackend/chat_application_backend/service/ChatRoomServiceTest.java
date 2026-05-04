package com.chatbackend.chat_application_backend.service;

import com.chatbackend.chat_application_backend.entity.ChatRoom;
import com.chatbackend.chat_application_backend.entity.User;
import com.chatbackend.chat_application_backend.repository.ChatRepository;
import com.chatbackend.chat_application_backend.repository.UserRepository;
import com.chatbackend.chat_application_backend.service.serviceImpl.ChatRoomServiceImpl;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@ExtendWith(MockitoExtension.class)
public class ChatRoomServiceTest {
    @Mock
    private ChatRepository chatRoomRepository;

    @InjectMocks
    private ChatRoomServiceImpl chatRoomService;

    @Mock
    private UserRepository userRepository;

    @Test
    void shouldCreateChatRoom() {

        User user1 = new User();
        user1.setId(1L);

        User user2 = new User();
        user2.setId(2L);

        ChatRoom room = new ChatRoom();
        room.setId(1L);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user1));
        when(userRepository.findById(2L)).thenReturn(Optional.of(user2));

        when(chatRoomRepository.save(any(ChatRoom.class))).thenReturn(room);

        ChatRoom result = chatRoomService.createPrivateChat(1L, 2L);

        assertNotNull(result);
        assertEquals(1L, result.getId());
    }
}
