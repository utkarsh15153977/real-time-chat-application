package com.chatApplication.message_service.kafka;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka producer configuration for the message-created event pipeline.
 * <p>
 * Configures a {@link ProducerFactory} and {@link KafkaTemplate} that
 * serialize {@link MessageCreatedEvent} payloads as JSON with String keys.
 * <p>
 * The {@code chatRoomId} is used as the Kafka message key, ensuring
 * all events for a given conversation are partitioned to the same broker
 * partition and processed in order.
 */
@Configuration
public class KafkaProducerConfig {

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    @Bean
    public ProducerFactory<String, MessageCreatedEvent> messageCreatedProducerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);

        // Reliability: wait for all in-sync replicas to acknowledge
        config.put(ProducerConfig.ACKS_CONFIG, "all");
        // Retry on transient failures
        config.put(ProducerConfig.RETRIES_CONFIG, 3);
        config.put(ProducerConfig.RETRY_BACKOFF_MS_CONFIG, 1000);
        // Batch for throughput (16KB default, 5ms linger)
        config.put(ProducerConfig.BATCH_SIZE_CONFIG, 16384);
        config.put(ProducerConfig.LINGER_MS_CONFIG, 5);

        return new DefaultKafkaProducerFactory<>(config);
    }

    @Bean
    public KafkaTemplate<String, MessageCreatedEvent> messageCreatedKafkaTemplate() {
        KafkaTemplate<String, MessageCreatedEvent> template =
                new KafkaTemplate<>(messageCreatedProducerFactory());
        template.setDefaultTopic("chat.message-created");
        return template;
    }
}
