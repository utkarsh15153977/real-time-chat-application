package com.chatbackend.chat_application_backend.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.chatbackend.chat_application_backend.config.TestSecurityConfig;
import com.chatbackend.chat_application_backend.entity.ChatRoom;
import com.chatbackend.chat_application_backend.entity.Message;
import com.chatbackend.chat_application_backend.entity.User;
import com.chatbackend.chat_application_backend.repository.ChatRepository;
import com.chatbackend.chat_application_backend.repository.MessageRepository;
import com.chatbackend.chat_application_backend.repository.UserRepository;
import com.chatbackend.chat_application_backend.security.CustomUserDetailsService;
import com.chatbackend.chat_application_backend.security.JwtUtil;
import com.chatbackend.chat_application_backend.service.MessageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.List;
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

    @MockBean
    private MessageRepository messageRepository;

    @MockBean
    private ChatRepository chatRepository;

    // Three distinct users for authorization testing
    private User userA;  // attacker
    private User userB;  // sender / chat participant
    private User userC;  // recipient / chat participant
    private User userD;  // unrelated user (not in any chat)

    private ChatRoom chatRoom;
    private Message message;

    @BeforeEach
    void setUp() {
        userA = new User();
        userA.setId(1L);
        userA.setEmail("alice@example.com");
        userA.setName("Alice");

        userB = new User();
        userB.setId(2L);
        userB.setEmail("bob@example.com");
        userB.setName("Bob");

        userC = new User();
        userC.setId(3L);
        userC.setEmail("charlie@example.com");
        userC.setName("Charlie");

        userD = new User();
        userD.setId(4L);
        userD.setEmail("dave@example.com");
        userD.setName("Dave");

        chatRoom = new ChatRoom();
        chatRoom.setId(10L);
        chatRoom.setParticipants(Arrays.asList(userB, userC));

        message = new Message();
        message.setId(100L);
        message.setSender(userB);
        message.setChatRoom(chatRoom);
        message.setContent("Hello Charlie");

        when(userRepository.findByEmail("alice@example.com"))
                .thenReturn(Optional.of(userA));
        when(userRepository.findByEmail("bob@example.com"))
                .thenReturn(Optional.of(userB));
        when(userRepository.findByEmail("charlie@example.com"))
                .thenReturn(Optional.of(userC));
        when(userRepository.findByEmail("dave@example.com"))
                .thenReturn(Optional.of(userD));
    }

    // ================================================================
    // TEST 1: Chat Room Participant Check
    // ================================================================
    @Nested
    @DisplayName("GET /api/messages/chat/{chatRoomId} — Authorization")
    class GetMessagesAuthorizationTests {

        @Test
        @DisplayName("should DENY User A (non-participant) reading chat room messages")
        void getMessages_userANotParticipant_returns403() throws Exception {
            when(chatRepository.findById(10L)).thenReturn(Optional.of(chatRoom));

            mockMvc.perform(get("/api/messages/chat/{chatRoomId}", 10L)
                            .with(csrf())
                            .with(user("alice@example.com")))
                    .andExpect(status().isForbidden());

            verify(messageService, never()).getMessagesByChatRoom(anyLong());
        }

        @Test
        @DisplayName("should ALLOW User B (participant) reading chat room messages")
        void getMessages_userBParticipant_returns200() throws Exception {
            when(chatRepository.findById(10L)).thenReturn(Optional.of(chatRoom));
            when(messageService.getMessagesByChatRoom(10L)).thenReturn(List.of(message));

            mockMvc.perform(get("/api/messages/chat/{chatRoomId}", 10L)
                            .with(csrf())
                            .with(user("bob@example.com")))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("should ALLOW User C (participant) reading chat room messages")
        void getMessages_userCParticipant_returns200() throws Exception {
            when(chatRepository.findById(10L)).thenReturn(Optional.of(chatRoom));
            when(messageService.getMessagesByChatRoom(10L)).thenReturn(List.of(message));

            mockMvc.perform(get("/api/messages/chat/{chatRoomId}", 10L)
                            .with(csrf())
                            .with(user("charlie@example.com")))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("should DENY User D (non-participant) reading chat room messages")
        void getMessages_userDNotParticipant_returns403() throws Exception {
            when(chatRepository.findById(10L)).thenReturn(Optional.of(chatRoom));

            mockMvc.perform(get("/api/messages/chat/{chatRoomId}", 10L)
                            .with(csrf())
                            .with(user("dave@example.com")))
                    .andExpect(status().isForbidden());
        }
    }

    // ================================================================
    // TEST 2: Mark Read Authorization
    // ================================================================
    @Nested
    @DisplayName("PUT /api/messages/{messageId}/read — Authorization")
    class MarkReadAuthorizationTests {

        @Test
        @DisplayName("should DENY User A (non-participant) marking message as read")
        void markRead_userANotParticipant_returns403() throws Exception {
            when(messageRepository.findById(100L)).thenReturn(Optional.of(message));

            mockMvc.perform(put("/api/messages/{messageId}/read", 100L)
                            .with(csrf())
                            .with(user("alice@example.com")))
                    .andExpect(status().isForbidden());

            verify(messageService, never()).markMessageRead(anyLong());
        }

        @Test
        @DisplayName("should ALLOW User B (sender/participant) marking message as read")
        void markRead_userBParticipant_returns200() throws Exception {
            when(messageRepository.findById(100L)).thenReturn(Optional.of(message));

            mockMvc.perform(put("/api/messages/{messageId}/read", 100L)
                            .with(csrf())
                            .with(user("bob@example.com")))
                    .andExpect(status().isOk());

            verify(messageService).markMessageRead(100L);
        }

        @Test
        @DisplayName("should ALLOW User C (recipient/participant) marking message as read")
        void markRead_userCParticipant_returns200() throws Exception {
            when(messageRepository.findById(100L)).thenReturn(Optional.of(message));

            mockMvc.perform(put("/api/messages/{messageId}/read", 100L)
                            .with(csrf())
                            .with(user("charlie@example.com")))
                    .andExpect(status().isOk());
        }
    }

    // ================================================================
    // TEST 3: Delete Authorization
    // ================================================================
    @Nested
    @DisplayName("DELETE /api/messages/{messageId} — Authorization")
    class DeleteAuthorizationTests {

        @Test
        @DisplayName("should DENY User A (non-sender) deleting message")
        void deleteUserANotSender_returns403() throws Exception {
            when(messageRepository.findById(100L)).thenReturn(Optional.of(message));

            mockMvc.perform(delete("/api/messages/{messageId}", 100L)
                            .with(csrf())
                            .with(user("alice@example.com")))
                    .andExpect(status().isForbidden());

            verify(messageService, never()).deleteMessage(anyLong());
        }

        @Test
        @DisplayName("should DENY User C (non-sender) deleting User B's message")
        void deleteUserCNotSender_returns403() throws Exception {
            when(messageRepository.findById(100L)).thenReturn(Optional.of(message));

            mockMvc.perform(delete("/api/messages/{messageId}", 100L)
                            .with(csrf())
                            .with(user("charlie@example.com")))
                    .andExpect(status().isForbidden());

            verify(messageService, never()).deleteMessage(anyLong());
        }

        @Test
        @DisplayName("should ALLOW User B (sender) deleting own message")
        void deleteUserBSender_returns204() throws Exception {
            when(messageRepository.findById(100L)).thenReturn(Optional.of(message));

            mockMvc.perform(delete("/api/messages/{messageId}", 100L)
                            .with(csrf())
                            .with(user("bob@example.com")))
                    .andExpect(status().isNoContent());

            verify(messageService).deleteMessage(100L);
        }
    }

    // ================================================================
    // TEST 4: Delivered — userId must be derived from auth
    // ================================================================
    @Nested
    @DisplayName("PUT /api/messages/{messageId}/delivered — Authorization")
    class MarkDeliveredAuthorizationTests {

        @Test
        @DisplayName("should DENY User A (non-participant) marking delivered")
        void markDelivered_userANotParticipant_returns403() throws Exception {
            when(messageRepository.findById(100L)).thenReturn(Optional.of(message));

            mockMvc.perform(put("/api/messages/{messageId}/delivered", 100L)
                            .with(csrf())
                            .with(user("alice@example.com")))
                    .andExpect(status().isForbidden());

            verify(messageService, never()).markAsDelivered(anyLong(), anyLong());
        }

        @Test
        @DisplayName("should ALLOW User B (participant) marking delivered — uses auth-derived userId")
        void markDelivered_userBParticipant_returns200() throws Exception {
            when(messageRepository.findById(100L)).thenReturn(Optional.of(message));

            mockMvc.perform(put("/api/messages/{messageId}/delivered", 100L)
                            .with(csrf())
                            .with(user("bob@example.com")))
                    .andExpect(status().isOk());

            verify(messageService).markAsDelivered(100L, 2L);
        }

        @Test
        @DisplayName("should ALLOW User C (participant) marking delivered — uses auth-derived userId")
        void markDelivered_userCParticipant_returns200() throws Exception {
            when(messageRepository.findById(100L)).thenReturn(Optional.of(message));

            mockMvc.perform(put("/api/messages/{messageId}/delivered", 100L)
                            .with(csrf())
                            .with(user("charlie@example.com")))
                    .andExpect(status().isOk());

            verify(messageService).markAsDelivered(100L, 3L);
        }
    }

    // ================================================================
    // TEST 5: Read-All — userId must be derived from auth
    // ================================================================
    @Nested
    @DisplayName("PUT /api/messages/chat/{chatRoomId}/read-all — Authorization")
    class MarkAllReadAuthorizationTests {

        @Test
        @DisplayName("should DENY User A (non-participant) marking all as read")
        void markAllRead_userANotParticipant_returns403() throws Exception {
            when(chatRepository.findById(10L)).thenReturn(Optional.of(chatRoom));

            mockMvc.perform(put("/api/messages/chat/{chatRoomId}/read-all", 10L)
                            .with(csrf())
                            .with(user("alice@example.com")))
                    .andExpect(status().isForbidden());

            verify(messageService, never()).markAsRead(anyLong(), anyLong());
        }

        @Test
        @DisplayName("should ALLOW User B (participant) — uses auth-derived userId")
        void markAllRead_userBParticipant_returns200() throws Exception {
            when(chatRepository.findById(10L)).thenReturn(Optional.of(chatRoom));

            mockMvc.perform(put("/api/messages/chat/{chatRoomId}/read-all", 10L)
                            .with(csrf())
                            .with(user("bob@example.com")))
                    .andExpect(status().isOk());

            verify(messageService).markAsRead(10L, 2L);
        }
    }

    // ================================================================
    // TEST 6: Unread Count — userId must be derived from auth
    // ================================================================
    @Nested
    @DisplayName("GET /api/messages/chat/{chatRoomId}/unread-count — Authorization")
    class UnreadCountAuthorizationTests {

        @Test
        @DisplayName("should DENY User A (non-participant) getting unread count")
        void unreadCount_userANotParticipant_returns403() throws Exception {
            when(chatRepository.findById(10L)).thenReturn(Optional.of(chatRoom));

            mockMvc.perform(get("/api/messages/chat/{chatRoomId}/unread-count", 10L)
                            .with(csrf())
                            .with(user("alice@example.com")))
                    .andExpect(status().isForbidden());

            verify(messageService, never()).getUnreadMessageCount(anyLong(), anyLong());
        }

        @Test
        @DisplayName("should ALLOW User B (participant) — uses auth-derived userId")
        void unreadCount_userBParticipant_returns200() throws Exception {
            when(chatRepository.findById(10L)).thenReturn(Optional.of(chatRoom));
            when(messageService.getUnreadMessageCount(2L, 10L)).thenReturn(3L);

            mockMvc.perform(get("/api/messages/chat/{chatRoomId}/unread-count", 10L)
                            .with(csrf())
                            .with(user("bob@example.com")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.unreadCount").value(3));
        }
    }

    // ================================================================
    // KAN-6 Regression: Send Message Tests (preserved)
    // ================================================================
    @Nested
    @DisplayName("POST /api/messages — KAN-6 Regression")
    class SendMessageTests {

        @Test
        @DisplayName("should send message using authenticated user ID")
        void shouldSendMessage() throws Exception {
            Message savedMessage = new Message();
            savedMessage.setId(200L);
            savedMessage.setSender(userB);
            savedMessage.setChatRoom(chatRoom);
            savedMessage.setContent("Hello");

            when(messageService.sendMessage(eq(2L), eq("Hello"), eq(10L)))
                    .thenReturn(savedMessage);

            mockMvc.perform(post("/api/messages")
                            .with(csrf())
                            .with(user("bob@example.com"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                            {
                              "chatRoomId":10,
                              "content":"Hello"
                            }
                            """))
                    .andExpect(status().isOk());

            verify(messageService).sendMessage(eq(2L), eq("Hello"), eq(10L));
        }

        @Test
        @DisplayName("should ignore client senderId and use authenticated user")
        void shouldIgnoreClientSenderId() throws Exception {
            Message savedMessage = new Message();
            savedMessage.setId(300L);
            savedMessage.setSender(userA);
            savedMessage.setChatRoom(chatRoom);
            savedMessage.setContent("Hello");

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
}
