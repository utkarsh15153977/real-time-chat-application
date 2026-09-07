package com.chatApplication.notification_service.service.impl;

import com.chatApplication.notification_service.dto.MessageEvent;
import com.chatApplication.notification_service.dto.NotificationEvent;
import com.chatApplication.notification_service.dto.NotificationResponse;
import com.chatApplication.notification_service.entity.Notification;
import com.chatApplication.notification_service.entity.NotificationType;
import com.chatApplication.notification_service.exception.NotificationNotFoundException;
import com.chatApplication.notification_service.kafka.NotificationProducer;
import com.chatApplication.notification_service.mapper.NotificationMapper;
import com.chatApplication.notification_service.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    @Mock
    private NotificationRepository repository;
    @Mock
    private NotificationMapper mapper;
    @Mock
    private SimpMessagingTemplate messagingTemplate;
    @Mock
    private NotificationProducer notificationProducer;

    @InjectMocks
    private NotificationServiceImpl notificationService;

    private Notification notification;
    private NotificationResponse notificationResponse;
    private NotificationEvent notificationEvent;
    private MessageEvent messageEvent;

    @BeforeEach
    void setUp() {
        notification = Notification.builder()
                .notificationId(1L)
                .senderId("user-1")
                .receiverId("user-2")
                .title("New Message")
                .message("Hello!")
                .notificationType(NotificationType.MESSAGE)
                .readStatus(false)
                .createdAt(LocalDateTime.now())
                .build();

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

        notificationEvent = NotificationEvent.builder()
                .notificationId(1L)
                .senderId("user-1")
                .receiverId("user-2")
                .title("New Message")
                .message("Hello!")
                .notificationType(NotificationType.MESSAGE)
                .createdAt(LocalDateTime.now())
                .build();

        messageEvent = MessageEvent.builder()
                .messageId(1L)
                .senderId("user-1")
                .receiverId("user-2")
                .content("Hello!")
                .status("SENT")
                .timestamp(LocalDateTime.now())
                .build();
    }

    @Test
    void createNotification_shouldSaveAndReturnResponse() {
        when(repository.save(any(Notification.class))).thenReturn(notification);
        when(mapper.toResponse(any(Notification.class))).thenReturn(notificationResponse);
        when(mapper.toEvent(any(Notification.class))).thenReturn(notificationEvent);

        NotificationResponse result = notificationService.createNotification(messageEvent);

        assertNotNull(result);
        assertEquals("user-2", result.getReceiverId());
        verify(repository, times(1)).save(any(Notification.class));
        verify(notificationProducer, times(1)).publish(any(NotificationEvent.class));
        verify(messagingTemplate, times(1)).convertAndSendToUser(eq("user-2"), eq("/queue/notifications"), any());
    }

    @Test
    void getAllNotifications_shouldReturnList() {
        when(repository.findByReceiverIdOrderByCreatedAtDesc("user-2"))
                .thenReturn(List.of(notification));
        when(mapper.toResponse(notification)).thenReturn(notificationResponse);

        List<NotificationResponse> result = notificationService.getAllNotifications("user-2");

        assertEquals(1, result.size());
        verify(repository).findByReceiverIdOrderByCreatedAtDesc("user-2");
    }

    @Test
    void getUnreadNotifications_shouldReturnList() {
        when(repository.findByReceiverIdAndReadStatusFalseOrderByCreatedAtDesc("user-2"))
                .thenReturn(List.of(notification));
        when(mapper.toResponse(notification)).thenReturn(notificationResponse);

        List<NotificationResponse> result = notificationService.getUnreadNotifications("user-2");

        assertEquals(1, result.size());
        assertFalse(result.get(0).getReadStatus());
    }

    @Test
    void markAsRead_shouldSetReadStatusAndReturn() {
        when(repository.findById(1L)).thenReturn(Optional.of(notification));
        when(repository.save(any(Notification.class))).thenReturn(notification);
        when(mapper.toResponse(any(Notification.class))).thenReturn(notificationResponse);

        NotificationResponse result = notificationService.markAsRead(1L);

        assertNotNull(result);
        verify(repository).save(any(Notification.class));
    }

    @Test
    void markAsRead_whenNotFound_shouldThrowException() {
        when(repository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(NotificationNotFoundException.class,
                () -> notificationService.markAsRead(999L));
    }

    @Test
    void markAllAsRead_shouldUpdateAllUnread() {
        when(repository.findByReceiverIdAndReadStatusFalseOrderByCreatedAtDesc("user-2"))
                .thenReturn(List.of(notification));
        when(repository.saveAll(any())).thenReturn(List.of(notification));

        notificationService.markAllAsRead("user-2");

        assertTrue(notification.getReadStatus());
        verify(repository).saveAll(any());
    }

    @Test
    void deleteNotification_shouldDelete() {
        when(repository.findById(1L)).thenReturn(Optional.of(notification));
        doNothing().when(repository).delete(any(Notification.class));

        notificationService.deleteNotification(1L);

        verify(repository).delete(notification);
    }

    @Test
    void deleteNotification_whenNotFound_shouldThrowException() {
        when(repository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(NotificationNotFoundException.class,
                () -> notificationService.deleteNotification(999L));
    }

    @Test
    void getUnreadCount_shouldReturnCount() {
        when(repository.countByReceiverIdAndReadStatusFalse("user-2")).thenReturn(3L);

        long count = notificationService.getUnreadCount("user-2");

        assertEquals(3L, count);
    }
}
