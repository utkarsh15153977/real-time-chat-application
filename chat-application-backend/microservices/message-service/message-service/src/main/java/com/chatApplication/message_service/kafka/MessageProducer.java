package com.chatApplication.message_service.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class MessageProducer {
    private final KafkaTemplate<String, MessageEvent>
            kafkaTemplate;

    private static final String TOPIC =
            "message-events";

    public void publish(
            MessageEvent event) {
        log.info("Publishing Event: {}", event);
        kafkaTemplate.send(
                TOPIC,
                event);
    }
}
