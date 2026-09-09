package com.chatApplication.message_service.kafka;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

/**
 * Publisher for chat message events to Apache Kafka.
 * <p>
 * Events are published to the {@code chat.message-created} topic with
 * {@code chatRoomId} as the partition key, guaranteeing in-order delivery
 * per conversation.
 * <p>
 * Publication failures are logged but do not block the caller. The
 * message has already been persisted to PostgreSQL at this point,
 * so Kafka failures only affect downstream consumers (notifications,
 * search indexing, analytics), not the core message delivery.
 */
@Slf4j
@Service
public class MessageEventPublisher {

    private final KafkaTemplate<String, MessageCreatedEvent> messageCreatedKafkaTemplate;

    public MessageEventPublisher(
            @Qualifier("messageCreatedKafkaTemplate") KafkaTemplate<String, MessageCreatedEvent> messageCreatedKafkaTemplate) {
        this.messageCreatedKafkaTemplate = messageCreatedKafkaTemplate;
    }

    private static final String TOPIC = "chat.message-created";

    /**
     * Publishes a {@link MessageCreatedEvent} to the chat.message-created topic.
     * <p>
     * The {@code chatRoomId} is used as the Kafka key to ensure all events
     * for a given conversation land on the same partition (in-order guarantee).
     *
     * @param event the message-created event to publish
     * @return a CompletableFuture that completes with the send result
     */
    public CompletableFuture<SendResult<String, MessageCreatedEvent>> publishMessageCreatedEvent(
            MessageCreatedEvent event) {

        String key = event.getChatRoomId();

        log.debug("Publishing MessageCreatedEvent: messageId={}, room={}, sender={}, recipient={}",
                event.getMessageId(), key, event.getSenderId(), event.getRecipientId());

        CompletableFuture<SendResult<String, MessageCreatedEvent>> future =
                messageCreatedKafkaTemplate.send(TOPIC, key, event);

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to publish MessageCreatedEvent: messageId={}, room={}, error={}",
                        event.getMessageId(), key, ex.getMessage());
            } else {
                log.info("MessageCreatedEvent published: messageId={}, room={}, partition={}, offset={}",
                        event.getMessageId(), key,
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            }
        });

        return future;
    }
}
