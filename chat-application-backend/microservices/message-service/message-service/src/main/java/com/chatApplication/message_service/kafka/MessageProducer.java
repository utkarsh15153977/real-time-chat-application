package com.chatApplication.message_service.kafka;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class MessageProducer {

    private final KafkaTemplate<String, MessageEvent> kafkaTemplate;

    private static final String TOPIC = "message-events";

    public MessageProducer(
            @Qualifier("messageEventKafkaTemplate") KafkaTemplate<String, MessageEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publish(MessageEvent event) {
        log.info("Publishing Event: {}", event);
        kafkaTemplate.send(TOPIC, event);
    }
}
