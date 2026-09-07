package com.chatApplication.notification_service.mapper;

import com.chatApplication.notification_service.dto.NotificationEvent;
import com.chatApplication.notification_service.dto.NotificationResponse;
import com.chatApplication.notification_service.entity.Notification;
import com.chatApplication.notification_service.entity.NotificationType;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class NotificationMapperTest {

    private final NotificationMapper mapper = new NotificationMapperImpl();

    @Test
    void toResponse_shouldMapCorrectly() {
        Notification notification = Notification.builder()
                .notificationId(1L)
                .senderId("user-1")
                .receiverId("user-2")
                .title("Test Title")
                .message("Test Message")
                .notificationType(NotificationType.MESSAGE)
                .readStatus(false)
                .createdAt(LocalDateTime.of(2025, 1, 1, 12, 0))
                .build();

        NotificationResponse response = mapper.toResponse(notification);

        assertNotNull(response);
        assertEquals(1L, response.getNotificationId());
        assertEquals("user-1", response.getSenderId());
        assertEquals("user-2", response.getReceiverId());
        assertEquals("Test Title", response.getTitle());
        assertEquals("Test Message", response.getMessage());
        assertEquals(NotificationType.MESSAGE, response.getNotificationType());
        assertFalse(response.getReadStatus());
        assertEquals(LocalDateTime.of(2025, 1, 1, 12, 0), response.getCreatedAt());
    }

    @Test
    void toEvent_shouldMapCorrectly() {
        Notification notification = Notification.builder()
                .notificationId(1L)
                .senderId("user-1")
                .receiverId("user-2")
                .title("Test Title")
                .message("Test Message")
                .notificationType(NotificationType.GROUP_INVITE)
                .readStatus(false)
                .createdAt(LocalDateTime.of(2025, 1, 1, 12, 0))
                .build();

        NotificationEvent event = mapper.toEvent(notification);

        assertNotNull(event);
        assertEquals(1L, event.getNotificationId());
        assertEquals("user-1", event.getSenderId());
        assertEquals("user-2", event.getReceiverId());
        assertEquals(NotificationType.GROUP_INVITE, event.getNotificationType());
    }

    @Test
    void toResponse_withNull_shouldReturnNull() {
        assertNull(mapper.toResponse(null));
    }

    @Test
    void toEvent_withNull_shouldReturnNull() {
        assertNull(mapper.toEvent(null));
    }
}
