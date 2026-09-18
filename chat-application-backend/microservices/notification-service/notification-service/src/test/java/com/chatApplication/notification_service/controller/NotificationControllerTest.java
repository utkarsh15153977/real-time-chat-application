package com.chatApplication.notification_service.controller;

import com.chatApplication.notification_service.dto.NotificationResponse;
import com.chatApplication.notification_service.entity.NotificationType;
import com.chatApplication.notification_service.exception.AccessDeniedException;
import com.chatApplication.notification_service.exception.NotificationNotFoundException;
import com.chatApplication.notification_service.service.NotificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(NotificationController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(NotificationControllerTest.TestSecurityConfig.class)
class NotificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private NotificationService notificationService;

    @Autowired
    private ObjectMapper objectMapper;

    private NotificationResponse notificationResponse;
    private NotificationResponse userBNotificationResponse;

    /**
     * Test security configuration that disables CSRF and permits all requests.
     * This allows testing controller authorization logic without the full
     * security filter chain (InternalSecurityFilter, JWT validation).
     */
    @EnableWebSecurity
    static class TestSecurityConfig {
        @Bean
        public SecurityFilterChain testFilterChain(HttpSecurity http) throws Exception {
            http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                    .anyRequest().permitAll()
                );
            return http.build();
        }
    }

    @BeforeEach
    void setUp() {
        notificationResponse = NotificationResponse.builder()
                .notificationId(1L)
                .senderId("user-1")
                .receiverId("user-A")
                .title("New Message")
                .message("Hello!")
                .notificationType(NotificationType.MESSAGE)
                .readStatus(false)
                .createdAt(LocalDateTime.now())
                .build();

        userBNotificationResponse = NotificationResponse.builder()
                .notificationId(2L)
                .senderId("user-3")
                .receiverId("user-B")
                .title("User B Notification")
                .message("Secret message for B")
                .notificationType(NotificationType.MESSAGE)
                .readStatus(false)
                .createdAt(LocalDateTime.now())
                .build();
    }

    // ==========================================
    // TEST 1: Notification Retrieval IDOR
    // ==========================================
    @Nested
    @DisplayName("TEST 1 - Notification Retrieval IDOR")
    class NotificationRetrievalIdor {

        @Test
        @DisplayName("should ignore receiverId path variable and use X-User-Id header")
        void getAllNotifications_shouldUseAuthenticatedUserId() throws Exception {
            when(notificationService.getAllNotifications("user-A"))
                    .thenReturn(List.of(notificationResponse));

            mockMvc.perform(get("/api/notifications/{receiverId}", "user-B")
                            .header("X-User-Id", "user-A"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].receiverId").value("user-A"));

            verify(notificationService).getAllNotifications("user-A");
            verify(notificationService, never()).getAllNotifications("user-B");
        }

        @Test
        @DisplayName("should return 403 when X-User-Id header is missing")
        void getAllNotifications_missingUserId_returns403() throws Exception {
            mockMvc.perform(get("/api/notifications/{receiverId}", "user-B"))
                    .andExpect(status().isForbidden());
        }
    }

    // ==========================================
    // TEST 2: Own Notification Retrieval
    // ==========================================
    @Nested
    @DisplayName("TEST 2 - Own Notification Retrieval")
    class OwnNotificationRetrieval {

        @Test
        @DisplayName("should return only authenticated user's notifications")
        void getAllNotifications_ownNotifications() throws Exception {
            when(notificationService.getAllNotifications("user-A"))
                    .thenReturn(List.of(notificationResponse));

            mockMvc.perform(get("/api/notifications/{receiverId}", "user-A")
                            .header("X-User-Id", "user-A"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].receiverId").value("user-A"));
        }

        @Test
        @DisplayName("should return only authenticated user's unread notifications")
        void getUnreadNotifications_ownNotifications() throws Exception {
            when(notificationService.getUnreadNotifications("user-A"))
                    .thenReturn(List.of(notificationResponse));

            mockMvc.perform(get("/api/notifications/unread/{receiverId}", "user-A")
                            .header("X-User-Id", "user-A"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].receiverId").value("user-A"));
        }
    }

    // ==========================================
    // TEST 3: Mark Read IDOR
    // ==========================================
    @Nested
    @DisplayName("TEST 3 - Mark Read IDOR")
    class MarkReadIdor {

        @Test
        @DisplayName("should reject marking another user's notification as read")
        void markAsRead_notOwner_shouldReturn403() throws Exception {
            when(notificationService.markAsRead(eq(1L), eq("user-A")))
                    .thenThrow(new AccessDeniedException(
                            "You do not have permission to modify this notification"));

            mockMvc.perform(put("/api/notifications/{notificationId}/read", 1L)
                            .header("X-User-Id", "user-A"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.message").value(
                            "You do not have permission to modify this notification"));
        }

        @Test
        @DisplayName("should allow marking own notification as read")
        void markAsRead_owner_shouldReturn200() throws Exception {
            NotificationResponse readResponse = NotificationResponse.builder()
                    .notificationId(1L)
                    .senderId("user-1")
                    .receiverId("user-A")
                    .title("New Message")
                    .message("Hello!")
                    .notificationType(NotificationType.MESSAGE)
                    .readStatus(true)
                    .createdAt(LocalDateTime.now())
                    .build();
            when(notificationService.markAsRead(eq(1L), eq("user-A")))
                    .thenReturn(readResponse);

            mockMvc.perform(put("/api/notifications/{notificationId}/read", 1L)
                            .header("X-User-Id", "user-A"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.readStatus").value(true));
        }
    }

    // ==========================================
    // TEST 4: Read-All IDOR
    // ==========================================
    @Nested
    @DisplayName("TEST 4 - Read-All IDOR")
    class ReadAllIdor {

        @Test
        @DisplayName("should ignore receiverId and use authenticated user for read-all")
        void markAllAsRead_shouldUseAuthenticatedUserId() throws Exception {
            doNothing().when(notificationService).markAllAsRead("user-A");

            mockMvc.perform(put("/api/notifications/read-all/{receiverId}", "user-B")
                            .header("X-User-Id", "user-A"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").value("All notifications marked as read."));

            verify(notificationService).markAllAsRead("user-A");
            verify(notificationService, never()).markAllAsRead("user-B");
        }
    }

    // ==========================================
    // TEST 5: Delete IDOR
    // ==========================================
    @Nested
    @DisplayName("TEST 5 - Delete IDOR")
    class DeleteIdor {

        @Test
        @DisplayName("should reject deleting another user's notification")
        void deleteNotification_notOwner_shouldReturn403() throws Exception {
            doThrow(new AccessDeniedException(
                    "You do not have permission to delete this notification"))
                    .when(notificationService).deleteNotification(eq(1L), eq("user-A"));

            mockMvc.perform(delete("/api/notifications/{notificationId}", 1L)
                            .header("X-User-Id", "user-A"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.message").value(
                            "You do not have permission to delete this notification"));
        }

        @Test
        @DisplayName("should allow deleting own notification")
        void deleteNotification_owner_shouldReturn200() throws Exception {
            doNothing().when(notificationService).deleteNotification(eq(1L), eq("user-A"));

            mockMvc.perform(delete("/api/notifications/{notificationId}", 1L)
                            .header("X-User-Id", "user-A"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").value("Notification deleted successfully."));
        }
    }

    // ==========================================
    // TEST 6: Unread Count IDOR
    // ==========================================
    @Nested
    @DisplayName("TEST 6 - Unread Count IDOR")
    class UnreadCountIdor {

        @Test
        @DisplayName("should ignore receiverId and use authenticated user for count")
        void getUnreadCount_shouldUseAuthenticatedUserId() throws Exception {
            when(notificationService.getUnreadCount("user-A")).thenReturn(5L);

            mockMvc.perform(get("/api/notifications/count/{receiverId}", "user-B")
                            .header("X-User-Id", "user-A"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").value(5));

            verify(notificationService).getUnreadCount("user-A");
            verify(notificationService, never()).getUnreadCount("user-B");
        }
    }

    // ==========================================
    // TEST 7: Unauthenticated Access
    // ==========================================
    @Nested
    @DisplayName("TEST 7 - Unauthenticated Access")
    class UnauthenticatedAccess {

        @Test
        @DisplayName("should reject GET without X-User-Id")
        void getAllNotifications_noAuth_returns403() throws Exception {
            mockMvc.perform(get("/api/notifications/{receiverId}", "user-A"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("should reject PUT without X-User-Id")
        void markAsRead_noAuth_returns403() throws Exception {
            mockMvc.perform(put("/api/notifications/{notificationId}/read", 1L))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("should reject DELETE without X-User-Id")
        void deleteNotification_noAuth_returns403() throws Exception {
            mockMvc.perform(delete("/api/notifications/{notificationId}", 1L))
                    .andExpect(status().isForbidden());
        }
    }

    // ==========================================
    // TEST 8: Notification Isolation
    // ==========================================
    @Nested
    @DisplayName("TEST 8 - Notification Isolation")
    class NotificationIsolation {

        @Test
        @DisplayName("should not leak User B notifications when User A requests")
        void getAllNotifications_isolation() throws Exception {
            when(notificationService.getAllNotifications("user-A"))
                    .thenReturn(List.of(notificationResponse));

            var result = mockMvc.perform(get("/api/notifications/{receiverId}", "user-B")
                            .header("X-User-Id", "user-A"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].receiverId").value("user-A"))
                    .andReturn();

            String responseBody = result.getResponse().getContentAsString();
            assertFalse(responseBody.contains("user-B"),
                    "Response should not contain User B notifications");

            verify(notificationService).getAllNotifications("user-A");
        }
    }

    // ==========================================
    // TEST 9: Notification Not Found
    // ==========================================
    @Nested
    @DisplayName("TEST 9 - Notification Not Found")
    class NotificationNotFound {

        @Test
        @DisplayName("should return 404 when notification not found for markAsRead")
        void markAsRead_notFound_shouldReturn404() throws Exception {
            when(notificationService.markAsRead(eq(999L), eq("user-A")))
                    .thenThrow(new NotificationNotFoundException(
                            "Notification not found with id : 999"));

            mockMvc.perform(put("/api/notifications/{notificationId}/read", 999L)
                            .header("X-User-Id", "user-A"))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("should return 404 when notification not found for delete")
        void deleteNotification_notFound_shouldReturn404() throws Exception {
            doThrow(new NotificationNotFoundException(
                    "Notification not found with id : 999"))
                    .when(notificationService).deleteNotification(eq(999L), eq("user-A"));

            mockMvc.perform(delete("/api/notifications/{notificationId}", 999L)
                            .header("X-User-Id", "user-A"))
                    .andExpect(status().isNotFound());
        }
    }
}
