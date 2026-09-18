package com.chatApplication.message_service.controller;

import com.chatApplication.message_service.dto.AttachmentRequest;
import com.chatApplication.message_service.dto.MessageRequest;
import com.chatApplication.message_service.dto.MessageResponse;
import com.chatApplication.message_service.entity.Message;
import com.chatApplication.message_service.entity.MessageStatus;
import com.chatApplication.message_service.exception.ForbiddenException;
import com.chatApplication.message_service.repository.MessageRepository;
import com.chatApplication.message_service.service.MessageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(MessageController.class)
@AutoConfigureMockMvc(addFilters = false)
class MessageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MessageService messageService;

    @MockBean
    private MessageRepository messageRepository;

    @Autowired
    private ObjectMapper objectMapper;

    // Three distinct users for authorization testing
    private static final String USER_A = "attacker-user-id";
    private static final String USER_B = "sender-user-id";
    private static final String USER_C = "recipient-user-id";

    private MessageResponse messageResponse;

    @BeforeEach
    void setUp() {
        messageResponse = MessageResponse.builder()
                .msgId(1L)
                .senderId(USER_B)
                .receiverId(USER_C)
                .content("Hello World")
                .status(MessageStatus.SENT)
                .sendTime(LocalDateTime.of(2025, 1, 15, 10, 30))
                .build();
    }

    private Message createMessage(Long id, String senderId, String receiverId) {
        return Message.builder()
                .msgId(id)
                .senderId(senderId)
                .receiverId(receiverId)
                .content("Test message")
                .status(MessageStatus.SENT)
                .timestamp(LocalDateTime.now())
                .build();
    }

    // ================================================================
    // TEST 1: Conversation IDOR
    // ================================================================
    @Nested
    @DisplayName("GET /api/messages/conversation — Authorization")
    class ConversationAuthorizationTests {

        @Test
        @DisplayName("should DENY User A accessing conversation between User B and User C")
        void getConversation_userAnotParticipant_returns403() throws Exception {
            mockMvc.perform(get("/api/messages/conversation")
                            .param("user1", USER_B)
                            .param("user2", USER_C)
                            .header("X-User-Id", USER_A))
                    .andExpect(status().isForbidden());

            verifyNoInteractions(messageService);
        }

        @Test
        @DisplayName("should ALLOW User B accessing own conversation with User C")
        void getConversation_userBisParticipant_returns200() throws Exception {
            when(messageService.getConversation(USER_B, USER_C))
                    .thenReturn(List.of(messageResponse));

            mockMvc.perform(get("/api/messages/conversation")
                            .param("user1", USER_B)
                            .param("user2", USER_C)
                            .header("X-User-Id", USER_B))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)));
        }

        @Test
        @DisplayName("should ALLOW User C accessing own conversation with User B")
        void getConversation_userCisParticipant_returns200() throws Exception {
            when(messageService.getConversation(USER_B, USER_C))
                    .thenReturn(List.of(messageResponse));

            mockMvc.perform(get("/api/messages/conversation")
                            .param("user1", USER_B)
                            .param("user2", USER_C)
                            .header("X-User-Id", USER_C))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)));
        }

        @Test
        @DisplayName("should return 401 when X-User-Id header is missing")
        void getConversation_noAuthHeader_returns401() throws Exception {
            mockMvc.perform(get("/api/messages/conversation")
                            .param("user1", USER_B)
                            .param("user2", USER_C))
                    .andExpect(status().isUnauthorized());

            verifyNoInteractions(messageService);
        }
    }

    // ================================================================
    // TEST 2: Individual Message IDOR
    // ================================================================
    @Nested
    @DisplayName("GET /api/messages/{messageId} — Authorization")
    class GetMessageAuthorizationTests {

        @Test
        @DisplayName("should DENY User A accessing User B's message to User C")
        void getMessage_userANotParticipant_returns403() throws Exception {
            Message message = createMessage(1L, USER_B, USER_C);
            when(messageRepository.findById(1L)).thenReturn(Optional.of(message));

            mockMvc.perform(get("/api/messages/{messageId}", 1L)
                            .header("X-User-Id", USER_A))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("should ALLOW User B (sender) accessing own message")
        void getMessage_userBSender_returns200() throws Exception {
            Message message = createMessage(1L, USER_B, USER_C);
            when(messageRepository.findById(1L)).thenReturn(Optional.of(message));
            when(messageService.getMessage(1L)).thenReturn(messageResponse);

            mockMvc.perform(get("/api/messages/{messageId}", 1L)
                            .header("X-User-Id", USER_B))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.msgId").value(1));
        }

        @Test
        @DisplayName("should ALLOW User C (recipient) accessing message")
        void getMessage_userCRecipient_returns200() throws Exception {
            Message message = createMessage(1L, USER_B, USER_C);
            when(messageRepository.findById(1L)).thenReturn(Optional.of(message));
            when(messageService.getMessage(1L)).thenReturn(messageResponse);

            mockMvc.perform(get("/api/messages/{messageId}", 1L)
                            .header("X-User-Id", USER_C))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.msgId").value(1));
        }
    }

    // ================================================================
    // TEST 3: Edit IDOR
    // ================================================================
    @Nested
    @DisplayName("PUT /api/messages/{messageId}/edit — Authorization")
    class EditMessageAuthorizationTests {

        @Test
        @DisplayName("should DENY User A editing User B's message")
        void editMessage_userANotSender_returns403() throws Exception {
            Message message = createMessage(1L, USER_B, USER_C);
            when(messageRepository.findById(1L)).thenReturn(Optional.of(message));

            mockMvc.perform(put("/api/messages/{messageId}/edit", 1L)
                            .param("content", "Hacked content")
                            .header("X-User-Id", USER_A))
                    .andExpect(status().isForbidden());

            verify(messageService, never()).editMessage(anyLong(), anyString());
        }

        @Test
        @DisplayName("should DENY User C (recipient) editing User B's message")
        void editMessage_userCNotSender_returns403() throws Exception {
            Message message = createMessage(1L, USER_B, USER_C);
            when(messageRepository.findById(1L)).thenReturn(Optional.of(message));

            mockMvc.perform(put("/api/messages/{messageId}/edit", 1L)
                            .param("content", "Hacked content")
                            .header("X-User-Id", USER_C))
                    .andExpect(status().isForbidden());

            verify(messageService, never()).editMessage(anyLong(), anyString());
        }

        @Test
        @DisplayName("should ALLOW User B (sender) editing own message")
        void editMessage_userBSender_returns200() throws Exception {
            Message message = createMessage(1L, USER_B, USER_C);
            MessageResponse editedResponse = MessageResponse.builder()
                    .msgId(1L).senderId(USER_B).receiverId(USER_C)
                    .content("Edited content").status(MessageStatus.SENT)
                    .sendTime(LocalDateTime.now()).build();

            when(messageRepository.findById(1L)).thenReturn(Optional.of(message));
            when(messageService.editMessage(eq(1L), eq("Edited content"))).thenReturn(editedResponse);

            mockMvc.perform(put("/api/messages/{messageId}/edit", 1L)
                            .param("content", "Edited content")
                            .header("X-User-Id", USER_B))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").value("Edited content"));
        }

        @Test
        @DisplayName("should verify original message unchanged after unauthorized edit attempt")
        void editMessage_unauthorizedDoesNotMutate() throws Exception {
            Message message = createMessage(1L, USER_B, USER_C);
            when(messageRepository.findById(1L)).thenReturn(Optional.of(message));

            mockMvc.perform(put("/api/messages/{messageId}/edit", 1L)
                            .param("content", "Hacked content")
                            .header("X-User-Id", USER_A))
                    .andExpect(status().isForbidden());

            verify(messageService, never()).editMessage(anyLong(), anyString());
        }
    }

    // ================================================================
    // TEST 4: Delete IDOR
    // ================================================================
    @Nested
    @DisplayName("DELETE /api/messages/{messageId} — Authorization")
    class DeleteMessageAuthorizationTests {

        @Test
        @DisplayName("should DENY User A deleting User B's message")
        void deleteMessage_userANotSender_returns403() throws Exception {
            Message message = createMessage(1L, USER_B, USER_C);
            when(messageRepository.findById(1L)).thenReturn(Optional.of(message));

            mockMvc.perform(delete("/api/messages/{messageId}", 1L)
                            .header("X-User-Id", USER_A))
                    .andExpect(status().isForbidden());

            verify(messageService, never()).deleteMessage(anyLong());
        }

        @Test
        @DisplayName("should ALLOW User B (sender) deleting own message")
        void deleteMessage_userBSender_returns200() throws Exception {
            Message message = createMessage(1L, USER_B, USER_C);
            when(messageRepository.findById(1L)).thenReturn(Optional.of(message));

            mockMvc.perform(delete("/api/messages/{messageId}", 1L)
                            .header("X-User-Id", USER_B))
                    .andExpect(status().isOk())
                    .andExpect(content().string("Message deleted successfully"));

            verify(messageService).deleteMessage(1L);
        }

        @Test
        @DisplayName("should verify message still exists after unauthorized delete attempt")
        void deleteMessage_unauthorizedDoesNotDelete() throws Exception {
            Message message = createMessage(1L, USER_B, USER_C);
            when(messageRepository.findById(1L)).thenReturn(Optional.of(message));

            mockMvc.perform(delete("/api/messages/{messageId}", 1L)
                            .header("X-User-Id", USER_A))
                    .andExpect(status().isForbidden());

            verify(messageRepository, never()).deleteById(anyLong());
        }
    }

    // ================================================================
    // TEST 5: Delivered Authorization
    // ================================================================
    @Nested
    @DisplayName("PUT /api/messages/{messageId}/delivered — Authorization")
    class MarkDeliveredAuthorizationTests {

        @Test
        @DisplayName("should DENY User A marking User B's message as delivered")
        void markDelivered_userANotRecipient_returns403() throws Exception {
            Message message = createMessage(1L, USER_B, USER_C);
            when(messageRepository.findById(1L)).thenReturn(Optional.of(message));

            mockMvc.perform(put("/api/messages/{messageId}/delivered", 1L)
                            .header("X-User-Id", USER_A))
                    .andExpect(status().isForbidden());

            verify(messageService, never()).markAsDelivered(anyLong());
        }

        @Test
        @DisplayName("should DENY User B (sender) marking own message as delivered")
        void markDelivered_userBSender_returns403() throws Exception {
            Message message = createMessage(1L, USER_B, USER_C);
            when(messageRepository.findById(1L)).thenReturn(Optional.of(message));

            mockMvc.perform(put("/api/messages/{messageId}/delivered", 1L)
                            .header("X-User-Id", USER_B))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("should ALLOW User C (recipient) marking message as delivered")
        void markDelivered_userCRecipient_returns200() throws Exception {
            Message message = createMessage(1L, USER_B, USER_C);
            MessageResponse deliveredResponse = MessageResponse.builder()
                    .msgId(1L).senderId(USER_B).receiverId(USER_C)
                    .status(MessageStatus.DELIVERED).sendTime(LocalDateTime.now()).build();

            when(messageRepository.findById(1L)).thenReturn(Optional.of(message));
            when(messageService.markAsDelivered(1L)).thenReturn(deliveredResponse);

            mockMvc.perform(put("/api/messages/{messageId}/delivered", 1L)
                            .header("X-User-Id", USER_C))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("DELIVERED"));
        }
    }

    // ================================================================
    // TEST 6: Seen Authorization
    // ================================================================
    @Nested
    @DisplayName("PUT /api/messages/{messageId}/seen — Authorization")
    class MarkSeenAuthorizationTests {

        @Test
        @DisplayName("should DENY User A marking User B's message as seen")
        void markSeen_userANotRecipient_returns403() throws Exception {
            Message message = createMessage(1L, USER_B, USER_C);
            when(messageRepository.findById(1L)).thenReturn(Optional.of(message));

            mockMvc.perform(put("/api/messages/{messageId}/seen", 1L)
                            .header("X-User-Id", USER_A))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("should ALLOW User C (recipient) marking message as seen")
        void markSeen_userCRecipient_returns200() throws Exception {
            Message message = createMessage(1L, USER_B, USER_C);
            MessageResponse seenResponse = MessageResponse.builder()
                    .msgId(1L).senderId(USER_B).receiverId(USER_C)
                    .status(MessageStatus.READ).sendTime(LocalDateTime.now()).build();

            when(messageRepository.findById(1L)).thenReturn(Optional.of(message));
            when(messageService.markAsSeen(1L)).thenReturn(seenResponse);

            mockMvc.perform(put("/api/messages/{messageId}/seen", 1L)
                            .header("X-User-Id", USER_C))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("READ"));
        }
    }

    // ================================================================
    // TEST 7: Unread Count IDOR
    // ================================================================
    @Nested
    @DisplayName("GET /api/messages/unread-count — Authorization")
    class UnreadCountAuthorizationTests {

        @Test
        @DisplayName("should DENY User A requesting unread count for User B→User C")
        void getUnreadCount_userANotReceiver_returns403() throws Exception {
            mockMvc.perform(get("/api/messages/unread-count")
                            .param("senderId", USER_B)
                            .param("receiverId", USER_C)
                            .header("X-User-Id", USER_A))
                    .andExpect(status().isForbidden());

            verifyNoInteractions(messageService);
        }

        @Test
        @DisplayName("should DENY User B (sender) requesting as receiver")
        void getUnreadCount_userBSenderAsReceiver_returns403() throws Exception {
            mockMvc.perform(get("/api/messages/unread-count")
                            .param("senderId", USER_B)
                            .param("receiverId", USER_C)
                            .header("X-User-Id", USER_B))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("should ALLOW User C (receiver) requesting own unread count")
        void getUnreadCount_userCReceiver_returns200() throws Exception {
            when(messageService.getUnreadCount(USER_B, USER_C)).thenReturn(5L);

            mockMvc.perform(get("/api/messages/unread-count")
                            .param("senderId", USER_B)
                            .param("receiverId", USER_C)
                            .header("X-User-Id", USER_C))
                    .andExpect(status().isOk())
                    .andExpect(content().string("5"));
        }
    }

    // ================================================================
    // TEST 8: Recent Messages IDOR
    // ================================================================
    @Nested
    @DisplayName("GET /api/messages/recent/{userId} — Authorization")
    class RecentMessagesAuthorizationTests {

        @Test
        @DisplayName("should DENY User A requesting User B's recent messages")
        void getRecentMessages_userANotOwner_returns403() throws Exception {
            mockMvc.perform(get("/api/messages/recent/{userId}", USER_B)
                            .header("X-User-Id", USER_A))
                    .andExpect(status().isForbidden());

            verifyNoInteractions(messageService);
        }

        @Test
        @DisplayName("should ALLOW User B requesting own recent messages")
        void getRecentMessages_userBOwner_returns200() throws Exception {
            when(messageService.getRecentMessages(USER_B))
                    .thenReturn(List.of(messageResponse));

            mockMvc.perform(get("/api/messages/recent/{userId}", USER_B)
                            .header("X-User-Id", USER_B))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)));
        }
    }

    // ================================================================
    // TEST 9: Seen-All Authorization
    // ================================================================
    @Nested
    @DisplayName("PUT /api/messages/seen-all — Authorization")
    class MarkAllSeenAuthorizationTests {

        @Test
        @DisplayName("should DENY User A marking User B→User C messages as seen")
        void markAllSeen_userANotReceiver_returns403() throws Exception {
            mockMvc.perform(put("/api/messages/seen-all")
                            .param("senderId", USER_B)
                            .param("receiverId", USER_C)
                            .header("X-User-Id", USER_A))
                    .andExpect(status().isForbidden());

            verifyNoInteractions(messageService);
        }

        @Test
        @DisplayName("should ALLOW User C (receiver) marking own messages as seen")
        void markAllSeen_userCReceiver_returns200() throws Exception {
            mockMvc.perform(put("/api/messages/seen-all")
                            .param("senderId", USER_B)
                            .param("receiverId", USER_C)
                            .header("X-User-Id", USER_C))
                    .andExpect(status().isOk())
                    .andExpect(content().string("Messages marked as seen"));

            verify(messageService).markAllAsSeen(USER_B, USER_C);
        }
    }

    // ================================================================
    // TEST 10: Exists Authorization
    // ================================================================
    @Nested
    @DisplayName("GET /api/messages/exists/{messageId} — Authorization")
    class ExistsAuthorizationTests {

        @Test
        @DisplayName("should DENY User A checking existence of User B's message")
        void exists_userANotParticipant_returns403() throws Exception {
            Message message = createMessage(1L, USER_B, USER_C);
            when(messageRepository.findById(1L)).thenReturn(Optional.of(message));

            mockMvc.perform(get("/api/messages/exists/{messageId}", 1L)
                            .header("X-User-Id", USER_A))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("should ALLOW User B checking own message existence")
        void exists_userBParticipant_returns200() throws Exception {
            Message message = createMessage(1L, USER_B, USER_C);
            when(messageRepository.findById(1L)).thenReturn(Optional.of(message));
            when(messageService.exists(1L)).thenReturn(true);

            mockMvc.perform(get("/api/messages/exists/{messageId}", 1L)
                            .header("X-User-Id", USER_B))
                    .andExpect(status().isOk())
                    .andExpect(content().string("true"));
        }
    }

    // ================================================================
    // KAN-6 Regression: Send Message Tests (preserved)
    // ================================================================
    @Nested
    @DisplayName("POST /api/messages — KAN-6 Regression")
    class SendMessageTests {

        @Test
        @DisplayName("should send message using authenticated user ID from X-User-Id header")
        void sendMessage_validRequest_returns200() throws Exception {
            MessageRequest request = new MessageRequest();
            request.setSenderId("attacker");
            request.setReceiverId(USER_C);
            request.setMessage("Hello World");

            when(messageService.sendMessage(any(MessageRequest.class)))
                    .thenReturn(messageResponse);

            mockMvc.perform(post("/api/messages")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("X-User-Id", USER_B)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.senderId").value(USER_B));

            verify(messageService).sendMessage(argThat(req ->
                    req.getSenderId().equals(USER_B)));
        }

        @Test
        @DisplayName("should return 401 when X-User-Id header is missing")
        void sendMessage_noAuthHeader_returns401() throws Exception {
            MessageRequest request = new MessageRequest();
            request.setSenderId(USER_B);
            request.setReceiverId(USER_C);
            request.setMessage("Hello World");

            mockMvc.perform(post("/api/messages")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized());

            verifyNoInteractions(messageService);
        }
    }
}
