package com.chatApplication.notification_service.kafka;

import com.chatApplication.notification_service.dto.MessageEvent;
import com.chatApplication.notification_service.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class NotificationConsumer {
    private final NotificationService notificationService;

    public NotificationConsumer(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @KafkaListener(
            topics = "message-events",
            groupId = "notification-group"
    )
    public void consumeMessage(MessageEvent event) {

        log.info("Received Message Event : {}", event);

        notificationService.createNotification(event);

        log.info("Notification created successfully for user : {}",
                event.getReceiverId());
    }
}
