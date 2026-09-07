package com.chatApplication.message_service.controller;

import com.chatApplication.message_service.dto.AttachmentRequest;
import com.chatApplication.message_service.dto.MessageRequest;
import com.chatApplication.message_service.dto.MessageResponse;
import com.chatApplication.message_service.entity.MessageStatus;
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
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

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

    @Autowired
    private ObjectMapper objectMapper;

    private MessageResponse messageResponse;

    @BeforeEach
    void setUp() {
        messageResponse = MessageResponse.builder()
                .msgId(1L)
                .senderId("user1")
                .receiverId("user2")
                .content("Hello World")
                .status(MessageStatus.SENT)
                .sendTime(LocalDateTime.of(2025, 1, 15, 10, 30))
                .build();
    }

    @Nested
    @DisplayName("POST /api/messages")
    class SendMessageTests {

        @Test
        @DisplayName("should send message")
        void sendMessage_validRequest_returns200() throws Exception {
            MessageRequest request = new MessageRequest();
            request.setSenderId("user1");
            request.setReceiverId("user2");
            request.setMessage("Hello World");

            when(messageService.sendMessage(any(MessageRequest.class)))
                    .thenReturn(messageResponse);

            mockMvc.perform(post("/api/messages")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.msgId").value(1))
                    .andExpect(jsonPath("$.senderId").value("user1"))
                    .andExpect(jsonPath("$.content").value("Hello World"));
        }
    }

    @Nested
    @DisplayName("GET /api/messages/{messageId}")
    class GetMessageTests {

        @Test
        @DisplayName("should return message by id")
        void getMessage_validId_returns200() throws Exception {
            when(messageService.getMessage(1L)).thenReturn(messageResponse);

            mockMvc.perform(get("/api/messages/{messageId}", 1L))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.msgId").value(1));
        }
    }

    @Nested
    @DisplayName("GET /api/messages/conversation")
    class GetConversationTests {

        @Test
        @DisplayName("should return conversation")
        void getConversation_validUsers_returns200() throws Exception {
            when(messageService.getConversation("user1", "user2"))
                    .thenReturn(List.of(messageResponse));

            mockMvc.perform(get("/api/messages/conversation")
                    .param("user1", "user1")
                    .param("user2", "user2"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)));
        }
    }

    @Nested
    @DisplayName("PUT /api/messages/{messageId}/delivered")
    class MarkAsDeliveredTests {

        @Test
        @DisplayName("should mark message as delivered")
        void markAsDelivered_validId_returns200() throws Exception {
            MessageResponse deliveredResponse = MessageResponse.builder()
                    .msgId(1L)
                    .senderId("user1")
                    .receiverId("user2")
                    .content("Hello World")
                    .status(MessageStatus.DELIVERED)
                    .sendTime(LocalDateTime.of(2025, 1, 15, 10, 30))
                    .build();

            when(messageService.markAsDelivered(1L)).thenReturn(deliveredResponse);

            mockMvc.perform(put("/api/messages/{messageId}/delivered", 1L))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("DELIVERED"));
        }
    }

    @Nested
    @DisplayName("PUT /api/messages/{messageId}/seen")
    class MarkAsSeenTests {

        @Test
        @DisplayName("should mark message as seen")
        void markAsSeen_validId_returns200() throws Exception {
            MessageResponse seenResponse = MessageResponse.builder()
                    .msgId(1L)
                    .senderId("user1")
                    .receiverId("user2")
                    .content("Hello World")
                    .status(MessageStatus.READ)
                    .sendTime(LocalDateTime.of(2025, 1, 15, 10, 30))
                    .build();

            when(messageService.markAsSeen(1L)).thenReturn(seenResponse);

            mockMvc.perform(put("/api/messages/{messageId}/seen", 1L))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("READ"));
        }
    }

    @Nested
    @DisplayName("PUT /api/messages/seen-all")
    class MarkAllAsSeenTests {

        @Test
        @DisplayName("should mark all messages as seen")
        void markAllAsSeen_validRequest_returns200() throws Exception {
            mockMvc.perform(put("/api/messages/seen-all")
                    .param("senderId", "user1")
                    .param("receiverId", "user2"))
                    .andExpect(status().isOk())
                    .andExpect(content().string("Messages marked as seen"));
        }
    }

    @Nested
    @DisplayName("PUT /api/messages/{messageId}/edit")
    class EditMessageTests {

        @Test
        @DisplayName("should edit message")
        void editMessage_validRequest_returns200() throws Exception {
            MessageResponse editedResponse = MessageResponse.builder()
                    .msgId(1L)
                    .senderId("user1")
                    .receiverId("user2")
                    .content("Edited content")
                    .status(MessageStatus.SENT)
                    .sendTime(LocalDateTime.of(2025, 1, 15, 10, 30))
                    .build();

            when(messageService.editMessage(eq(1L), eq("Edited content")))
                    .thenReturn(editedResponse);

            mockMvc.perform(put("/api/messages/{messageId}/edit", 1L)
                    .param("content", "Edited content"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").value("Edited content"));
        }
    }

    @Nested
    @DisplayName("DELETE /api/messages/{messageId}")
    class DeleteMessageTests {

        @Test
        @DisplayName("should delete message")
        void deleteMessage_validId_returns200() throws Exception {
            mockMvc.perform(delete("/api/messages/{messageId}", 1L))
                    .andExpect(status().isOk())
                    .andExpect(content().string("Message deleted successfully"));
        }
    }

    @Nested
    @DisplayName("GET /api/messages/unread-count")
    class GetUnreadCountTests {

        @Test
        @DisplayName("should return unread count")
        void getUnreadCount_validRequest_returns200() throws Exception {
            when(messageService.getUnreadCount("user1", "user2")).thenReturn(5L);

            mockMvc.perform(get("/api/messages/unread-count")
                    .param("senderId", "user1")
                    .param("receiverId", "user2"))
                    .andExpect(status().isOk())
                    .andExpect(content().string("5"));
        }
    }

    @Nested
    @DisplayName("GET /api/messages/recent/{userId}")
    class GetRecentMessagesTests {

        @Test
        @DisplayName("should return recent messages")
        void getRecentMessages_validUser_returns200() throws Exception {
            when(messageService.getRecentMessages("user1"))
                    .thenReturn(List.of(messageResponse));

            mockMvc.perform(get("/api/messages/recent/{userId}", "user1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)));
        }
    }

    @Nested
    @DisplayName("GET /api/messages/exists/{messageId}")
    class ExistsTests {

        @Test
        @DisplayName("should return true if message exists")
        void exists_validId_returns200() throws Exception {
            when(messageService.exists(1L)).thenReturn(true);

            mockMvc.perform(get("/api/messages/exists/{messageId}", 1L))
                    .andExpect(status().isOk())
                    .andExpect(content().string("true"));
        }
    }
}
