package com.chatApplication.message_service.kafka;

import com.chatApplication.message_service.service.PushNotificationService;
import com.chatApplication.message_service.service.UserPresenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer for the {@code chat.message-created} topic.
 * <p>
 * Listens with group-id {@code notification-group} to process events
 * published by {@link MessageEventPublisher}. This consumer is responsible
 * for triggering push notifications for offline recipients.
 * <p>
 * The presence check + push notification flow is delegated to existing
 * services, keeping this listener thin and focused on event deserialization.
 * <p>
 * Note: The same presence check is also performed in ChatWebSocketController
 * for immediate delivery. This consumer serves as a fallback path for
 * scenarios where the controller-side push was missed (e.g., race condition
 * where user goes offline between the controller check and FCM dispatch).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MessageCreatedEventListener {

    private final UserPresenceService userPresenceService;
    private final PushNotificationService pushNotificationService;

    /**
     * Consumes a MessageCreatedEvent from the chat.message-created topic.
     * <p>
     * If the recipient is offline, triggers an FCM push notification
     * to all registered devices.
     *
     * @param record the Kafka consumer record containing the event
     */
    @KafkaListener(
            topics = "${app.kafka.topics.message-created:chat.message-created}",
            groupId = "notification-group"
    )
    public void onMessageCreated(ConsumerRecord<String, MessageCreatedEvent> record) {
        MessageCreatedEvent event = record.value();

        if (event == null) {
            log.warn("Received null MessageCreatedEvent on partition={}, offset={}",
                    record.partition(), record.offset());
            return;
        }

        log.info("Consumed MessageCreatedEvent: messageId={}, room={}, partition={}, offset={}",
                event.getMessageId(), event.getChatRoomId(),
                record.partition(), record.offset());

        try {
            // Check if recipient is offline
            if (!userPresenceService.isUserConnected(event.getRecipientId())) {
                log.debug("Recipient {} is offline (via Kafka consumer), dispatching push",
                        event.getRecipientId());

                pushNotificationService.sendPushNotificationToUser(
                        event.getRecipientId(),
                        event.getSenderId(),
                        event.getContent(),
                        event.getChatRoomId());
            } else {
                log.debug("Recipient {} is online (via Kafka consumer), skipping push",
                        event.getRecipientId());
            }
        } catch (Exception e) {
            // Never let consumer failure poison the Kafka consumer thread.
            // The message is already persisted; push notification failure is non-critical.
            log.error("Failed to process MessageCreatedEvent for messageId={}: {}",
                    event.getMessageId(), e.getMessage());
        }
    }
}
