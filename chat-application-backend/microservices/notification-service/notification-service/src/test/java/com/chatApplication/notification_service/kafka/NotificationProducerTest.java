package com.chatApplication.notification_service.kafka;

import com.chatApplication.notification_service.dto.NotificationEvent;
import com.chatApplication.notification_service.entity.NotificationType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationProducerTest {

    @Mock
    private KafkaTemplate<String, NotificationEvent> kafkaTemplate;

    @InjectMocks
    private NotificationProducer notificationProducer;

    @Test
    void publish_shouldSendEventToKafka() {
        NotificationEvent event = NotificationEvent.builder()
                .notificationId(1L)
                .senderId("user-1")
                .receiverId("user-2")
                .title("New Message")
                .message("Hello!")
                .notificationType(NotificationType.MESSAGE)
                .createdAt(LocalDateTime.now())
                .build();

        when(kafkaTemplate.send(anyString(), any(NotificationEvent.class))).thenReturn(null);

        notificationProducer.publish(event);

        verify(kafkaTemplate, times(1)).send(eq("notification-events"), eq(event));
    }
}
