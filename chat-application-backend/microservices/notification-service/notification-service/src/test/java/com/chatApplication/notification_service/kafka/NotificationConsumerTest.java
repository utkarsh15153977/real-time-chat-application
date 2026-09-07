package com.chatApplication.notification_service.kafka;

import com.chatApplication.notification_service.dto.MessageEvent;
import com.chatApplication.notification_service.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationConsumerTest {

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private NotificationConsumer notificationConsumer;

    @Test
    void consumeMessage_shouldCallCreateNotification() {
        MessageEvent event = MessageEvent.builder()
                .messageId(1L)
                .senderId("user-1")
                .receiverId("user-2")
                .content("Test message")
                .status("SENT")
                .timestamp(LocalDateTime.now())
                .build();

        notificationConsumer.consumeMessage(event);

        verify(notificationService, times(1)).createNotification(event);
    }
}
