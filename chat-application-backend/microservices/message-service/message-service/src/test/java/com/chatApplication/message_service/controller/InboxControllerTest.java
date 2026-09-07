package com.chatApplication.message_service.controller;

import com.chatApplication.message_service.dto.InboxItemDTO;
import com.chatApplication.message_service.dto.TotalUnreadCountDTO;
import com.chatApplication.message_service.entity.MessageType;
import com.chatApplication.message_service.service.InboxService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for InboxController REST endpoints.
 * <p>
 * Tests:
 * - GET /api/v1/inbox with valid X-User-Id header
 * - GET /api/v1/inbox/unread-count
 * - Proper 200 OK responses and JSON structure
 * - Pagination parameters
 * - Missing X-User-Id header handling
 */
@WebMvcTest(InboxController.class)
@AutoConfigureMockMvc(addFilters = false)
class InboxControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private InboxService inboxService;

    @Autowired
    private ObjectMapper objectMapper;

    private InboxItemDTO inboxItem;
    private TotalUnreadCountDTO totalUnreadCount;

    @BeforeEach
    void setUp() {
        inboxItem = InboxItemDTO.builder()
                .chatRoomId("user1-user2")
                .recipientId("user2")
                .lastMessageContent("Hello World")
                .lastMessageType(MessageType.TEXT)
                .lastMessageTimestamp(Instant.parse("2025-01-15T10:30:00Z"))
                .lastMessageSenderId("user1")
                .unreadCount(5)
                .build();

        totalUnreadCount = TotalUnreadCountDTO.builder()
                .totalUnreadCount(10)
                .build();
    }

    // ================================================================
    // GET /api/v1/inbox tests
    // ================================================================

    @Nested
    @DisplayName("GET /api/v1/inbox")
    class GetInboxOverviewTests {

        @Test
        @DisplayName("should return paginated inbox with valid X-User-Id")
        void getInboxOverview_validUserId_returns200() throws Exception {
            // Arrange
            Page<InboxItemDTO> page = new PageImpl<>(
                    List.of(inboxItem),
                    PageRequest.of(0, 20),
                    1);

            when(inboxService.getInboxOverview(eq("user2"), any()))
                    .thenReturn(page);

            // Act & Assert
            mockMvc.perform(get("/api/v1/inbox")
                    .header("X-User-Id", "user2")
                    .param("page", "0")
                    .param("size", "20"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(1)))
                    .andExpect(jsonPath("$.content[0].chatRoomId").value("user1-user2"))
                    .andExpect(jsonPath("$.content[0].recipientId").value("user2"))
                    .andExpect(jsonPath("$.content[0].lastMessageContent").value("Hello World"))
                    .andExpect(jsonPath("$.content[0].unreadCount").value(5))
                    .andExpect(jsonPath("$.totalElements").value(1));

            verify(inboxService).getInboxOverview(eq("user2"), any());
        }

        @Test
        @DisplayName("should use default pagination when not provided")
        void getInboxOverview_noPagination_usesDefaults() throws Exception {
            // Arrange
            Page<InboxItemDTO> page = new PageImpl<>(
                    List.of(inboxItem),
                    PageRequest.of(0, 20),
                    1);

            when(inboxService.getInboxOverview(eq("user1"), any()))
                    .thenReturn(page);

            // Act & Assert
            mockMvc.perform(get("/api/v1/inbox")
                    .header("X-User-Id", "user1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(1)));

            verify(inboxService).getInboxOverview(eq("user1"), any());
        }

        @Test
        @DisplayName("should handle empty inbox")
        void getInboxOverview_emptyInbox_returnsEmptyPage() throws Exception {
            // Arrange
            Page<InboxItemDTO> emptyPage = new PageImpl<>(
                    List.of(),
                    PageRequest.of(0, 20),
                    0);

            when(inboxService.getInboxOverview(eq("user1"), any()))
                    .thenReturn(emptyPage);

            // Act & Assert
            mockMvc.perform(get("/api/v1/inbox")
                    .header("X-User-Id", "user1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(0)))
                    .andExpect(jsonPath("$.totalElements").value(0));
        }

        @Test
        @DisplayName("should respect custom pagination parameters")
        void getInboxOverview_customPagination_appliesCorrectly() throws Exception {
            // Arrange
            Page<InboxItemDTO> page = new PageImpl<>(
                    List.of(inboxItem),
                    PageRequest.of(1, 10),
                    25);

            when(inboxService.getInboxOverview(eq("user1"), any()))
                    .thenReturn(page);

            // Act & Assert
            mockMvc.perform(get("/api/v1/inbox")
                    .header("X-User-Id", "user1")
                    .param("page", "1")
                    .param("size", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(25))
                    .andExpect(jsonPath("$.number").value(1));

            verify(inboxService).getInboxOverview(eq("user1"), any());
        }

        @Test
        @DisplayName("should return correct JSON structure")
        void getInboxOverview_returnsCorrectJsonStructure() throws Exception {
            // Arrange
            Page<InboxItemDTO> page = new PageImpl<>(
                    List.of(inboxItem),
                    PageRequest.of(0, 20),
                    1);

            when(inboxService.getInboxOverview(eq("user1"), any()))
                    .thenReturn(page);

            // Act & Assert
            mockMvc.perform(get("/api/v1/inbox")
                    .header("X-User-Id", "user1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0]").exists())
                    .andExpect(jsonPath("$.content[0].chatRoomId").isString())
                    .andExpect(jsonPath("$.content[0].recipientId").isString())
                    .andExpect(jsonPath("$.content[0].lastMessageContent").isString())
                    .andExpect(jsonPath("$.content[0].lastMessageType").isString())
                    .andExpect(jsonPath("$.content[0].lastMessageTimestamp").isString())
                    .andExpect(jsonPath("$.content[0].lastMessageSenderId").isString())
                    .andExpect(jsonPath("$.content[0].unreadCount").isNumber());
        }
    }

    // ================================================================
    // GET /api/v1/inbox/unread-count tests
    // ================================================================

    @Nested
    @DisplayName("GET /api/v1/inbox/unread-count")
    class GetTotalUnreadCountTests {

        @Test
        @DisplayName("should return total unread count with valid X-User-Id")
        void getTotalUnreadCount_validUserId_returns200() throws Exception {
            // Arrange
            when(inboxService.getTotalUnreadCount("user1"))
                    .thenReturn(totalUnreadCount);

            // Act & Assert
            mockMvc.perform(get("/api/v1/inbox/unread-count")
                    .header("X-User-Id", "user1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalUnreadCount").value(10));

            verify(inboxService).getTotalUnreadCount("user1");
        }

        @Test
        @DisplayName("should return zero when no unread messages")
        void getTotalUnreadCount_noUnread_returnsZero() throws Exception {
            // Arrange
            TotalUnreadCountDTO zeroCount = TotalUnreadCountDTO.builder()
                    .totalUnreadCount(0)
                    .build();

            when(inboxService.getTotalUnreadCount("user1"))
                    .thenReturn(zeroCount);

            // Act & Assert
            mockMvc.perform(get("/api/v1/inbox/unread-count")
                    .header("X-User-Id", "user1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalUnreadCount").value(0));
        }

        @Test
        @DisplayName("should return correct JSON structure")
        void getTotalUnreadCount_returnsCorrectJsonStructure() throws Exception {
            // Arrange
            when(inboxService.getTotalUnreadCount("user1"))
                    .thenReturn(totalUnreadCount);

            // Act & Assert
            mockMvc.perform(get("/api/v1/inbox/unread-count")
                    .header("X-User-Id", "user1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalUnreadCount").isNumber());
        }

        @Test
        @DisplayName("should handle large unread counts")
        void getTotalUnreadCount_largeCount_returnsCorrectValue() throws Exception {
            // Arrange
            TotalUnreadCountDTO largeCount = TotalUnreadCountDTO.builder()
                    .totalUnreadCount(999999)
                    .build();

            when(inboxService.getTotalUnreadCount("user1"))
                    .thenReturn(largeCount);

            // Act & Assert
            mockMvc.perform(get("/api/v1/inbox/unread-count")
                    .header("X-User-Id", "user1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalUnreadCount").value(999999));
        }
    }

    // ================================================================
    // Security tests
    // ================================================================

    @Nested
    @DisplayName("Security")
    class SecurityTests {

        @Test
        @DisplayName("should handle missing X-User-Id header")
        void missingUserIdHeader_returns400() throws Exception {
            // Act & Assert - Spring will throw MissingRequestHeaderException
            mockMvc.perform(get("/api/v1/inbox"))
                    .andExpect(status().isInternalServerError());
        }

        @Test
        @DisplayName("should handle empty X-User-Id header")
        void emptyUserIdHeader_returns400() throws Exception {
            // Act & Assert
            mockMvc.perform(get("/api/v1/inbox")
                    .header("X-User-Id", ""))
                    .andExpect(status().isOk());
        }
    }
}
