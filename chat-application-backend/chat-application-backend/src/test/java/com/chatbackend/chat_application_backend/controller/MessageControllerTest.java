package com.chatbackend.chat_application_backend.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chatbackend.chat_application_backend.config.TestSecurityConfig;
import com.chatbackend.chat_application_backend.entity.ChatRoom;
import com.chatbackend.chat_application_backend.entity.Message;
import com.chatbackend.chat_application_backend.entity.User;
import com.chatbackend.chat_application_backend.repository.UserRepository;
import com.chatbackend.chat_application_backend.security.CustomUserDetailsService;
import com.chatbackend.chat_application_backend.security.JwtUtil;
import com.chatbackend.chat_application_backend.service.MessageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

@WebMvcTest(MessageController.class)
@Import(TestSecurityConfig.class)
public class MessageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MessageService messageService;

    @MockBean
    private JwtUtil jwtUtil;

    @MockBean
    private CustomUserDetailsService customUserDetailsService;

    @MockBean
    private UserRepository userRepository;

    private User authenticatedUser;

    @BeforeEach
    void setUp() {
        authenticatedUser = new User();
        authenticatedUser.setId(1L);
        authenticatedUser.setEmail("alice@example.com");
        authenticatedUser.setName("Alice");

        when(userRepository.findByEmail("alice@example.com"))
                .thenReturn(Optional.of(authenticatedUser));
    }

    private Message createMockMessage(Long id, User sender, Long chatRoomId, String content) {
        Message message = new Message();
        message.setId(id);
        message.setSender(sender);

        ChatRoom chatRoom = new ChatRoom();
        chatRoom.setId(chatRoomId);
        message.setChatRoom(chatRoom);
        message.setContent(content);
        return message;
    }

    @Test
    void shouldSendMessage() throws Exception {
        Message savedMessage = createMockMessage(100L, authenticatedUser, 10L, "Hello");

        when(messageService.sendMessage(eq(1L), eq("Hello"), eq(10L)))
                .thenReturn(savedMessage);

        mockMvc.perform(post("/api/messages")
                        .with(csrf())
                        .with(user("alice@example.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {
                          "chatRoomId":10,
                          "content":"Hello"
                        }
                        """))
                .andExpect(status().isOk());

        verify(messageService).sendMessage(eq(1L), eq("Hello"), eq(10L));
    }

    @Test
    void shouldIgnoreClientSenderIdAndUseAuthenticatedUser() throws Exception {
        Message savedMessage = createMockMessage(200L, authenticatedUser, 10L, "Hello");

        when(messageService.sendMessage(eq(1L), eq("Hello"), eq(10L)))
                .thenReturn(savedMessage);

        mockMvc.perform(post("/api/messages")
                        .with(csrf())
                        .with(user("alice@example.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {
                          "senderId":999,
                          "chatRoomId":10,
                          "content":"Hello"
                        }
                        """))
                .andExpect(status().isOk());

        verify(messageService).sendMessage(eq(1L), eq("Hello"), eq(10L));
    }

    @Test
    void senderImpersonationShouldUseAuthenticatedUserId() throws Exception {
        Message savedMessage = createMockMessage(300L, authenticatedUser, 10L, "Hello");

        when(messageService.sendMessage(eq(1L), eq("Hello"), eq(10L)))
                .thenReturn(savedMessage);

        mockMvc.perform(post("/api/messages")
                        .with(csrf())
                        .with(user("alice@example.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {
                          "senderId":999,
                          "chatRoomId":10,
                          "content":"Hello"
                        }
                        """))
                .andExpect(status().isOk());

        verify(messageService).sendMessage(eq(1L), eq("Hello"), eq(10L));
        verify(messageService, never()).sendMessage(eq(999L), anyString(), anyLong());
    }
}
