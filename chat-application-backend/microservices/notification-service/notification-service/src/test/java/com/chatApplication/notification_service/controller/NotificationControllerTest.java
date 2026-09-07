package com.chatApplication.notification_service.controller;

import com.chatApplication.notification_service.dto.NotificationResponse;
import com.chatApplication.notification_service.entity.NotificationType;
import com.chatApplication.notification_service.exception.NotificationNotFoundException;
import com.chatApplication.notification_service.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationControllerTest {

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private NotificationController notificationController;

    private NotificationResponse notificationResponse;

    @BeforeEach
    void setUp() {
        notificationResponse = NotificationResponse.builder()
                .notificationId(1L)
                .senderId("user-1")
                .receiverId("user-2")
                .title("New Message")
                .message("Hello!")
                .notificationType(NotificationType.MESSAGE)
                .readStatus(false)
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Test
    void getAllNotifications_shouldReturnList() {
        when(notificationService.getAllNotifications(anyString()))
                .thenReturn(List.of(notificationResponse));

        ResponseEntity<List<NotificationResponse>> result =
                notificationController.getAllNotifications("user-2");

        assertEquals(200, result.getStatusCodeValue());
        assertEquals(1, result.getBody().size());
        assertEquals("user-2", result.getBody().get(0).getReceiverId());
    }

    @Test
    void getUnreadNotifications_shouldReturnList() {
        when(notificationService.getUnreadNotifications(anyString()))
                .thenReturn(List.of(notificationResponse));

        ResponseEntity<List<NotificationResponse>> result =
                notificationController.getUnreadNotifications("user-2");

        assertEquals(200, result.getStatusCodeValue());
        assertFalse(result.getBody().get(0).getReadStatus());
    }

    @Test
    void markAsRead_shouldReturnUpdatedNotification() {
        NotificationResponse readResponse = NotificationResponse.builder()
                .notificationId(1L)
                .senderId("user-1")
                .receiverId("user-2")
                .title("New Message")
                .message("Hello!")
                .notificationType(NotificationType.MESSAGE)
                .readStatus(true)
                .createdAt(LocalDateTime.now())
                .build();
        when(notificationService.markAsRead(anyLong())).thenReturn(readResponse);

        ResponseEntity<NotificationResponse> result =
                notificationController.markAsRead(1L);

        assertEquals(200, result.getStatusCodeValue());
        assertTrue(result.getBody().getReadStatus());
    }

    @Test
    void markAllAsRead_shouldReturnSuccessMessage() {
        doNothing().when(notificationService).markAllAsRead(anyString());

        ResponseEntity<String> result =
                notificationController.markAllAsRead("user-2");

        assertEquals(200, result.getStatusCodeValue());
        assertEquals("All notifications marked as read.", result.getBody());
    }

    @Test
    void deleteNotification_shouldReturnSuccessMessage() {
        doNothing().when(notificationService).deleteNotification(anyLong());

        ResponseEntity<String> result =
                notificationController.deleteNotification(1L);

        assertEquals(200, result.getStatusCodeValue());
        assertEquals("Notification deleted successfully.", result.getBody());
    }

    @Test
    void getUnreadCount_shouldReturnCount() {
        when(notificationService.getUnreadCount(anyString())).thenReturn(5L);

        ResponseEntity<Long> result =
                notificationController.getUnreadCount("user-2");

        assertEquals(200, result.getStatusCodeValue());
        assertEquals(5L, result.getBody());
    }

    @Test
    void markAsRead_whenNotFound_shouldThrowException() {
        when(notificationService.markAsRead(anyLong()))
                .thenThrow(new NotificationNotFoundException("Notification not found with id : 999"));

        assertThrows(NotificationNotFoundException.class,
                () -> notificationController.markAsRead(999L));
    }
}
