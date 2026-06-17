package com.chatbackend.chat_application_backend.service;

import com.chatbackend.chat_application_backend.entity.ChatRoom;
import com.chatbackend.chat_application_backend.entity.User;
import com.chatbackend.chat_application_backend.repository.ChatRepository;
import com.chatbackend.chat_application_backend.repository.UserRepository;
import com.chatbackend.chat_application_backend.service.serviceImpl.ChatRoomServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional; // IMPORTANT: Add this
import static org.junit.jupiter.api.Assertions.assertNotNull; // Use JUnit assertions, not Hibernate
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ChatRoomServiceTest {
    @Mock
    private ChatRepository chatRoomRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SecurityContext securityContext;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private ChatRoomServiceImpl chatRoomService;

    @Test
    void shouldCreateChatRoom() {
        // 1. Setup Mock Security Context
        SecurityContextHolder.setContext(securityContext);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn("test@example.com");

        // 2. Setup Mock Data
        User currentUser = new User();
        currentUser.setId(1L);
        currentUser.setEmail("test@example.com");

        User user2 = new User();
        user2.setId(2L);

        ChatRoom room = new ChatRoom();
        room.setId(101L);

        // 3. Mock Repository Behaviors
        // Mock getCurrentUser() internals
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(currentUser));

        // Mock the findById calls inside createPrivateChat
        when(userRepository.findById(1L)).thenReturn(Optional.of(currentUser));
        when(userRepository.findById(2L)).thenReturn(Optional.of(user2));

        when(chatRoomRepository.save(any(ChatRoom.class))).thenReturn(room);

        // 4. Execute
        ChatRoom result = chatRoomService.createPrivateChat(1L, 2L);

        // 5. Assert
        assertNotNull(result);
        assertEquals(101L, result.getId());
    }
}