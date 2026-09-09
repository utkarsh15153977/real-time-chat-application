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
 * Kafka producer configuration for the message event pipelines.
 * <p>
 * Configures two {@link ProducerFactory}/{@link KafkaTemplate} pairs:
 * <ul>
 *   <li>{@code messageCreated} — for {@link MessageCreatedEvent} payloads
 *       published to the {@code chat.message-created} topic (WebSocket path).</li>
 *   <li>{@code message} — for {@link MessageEvent} payloads
 *       published to the {@code message-events} topic (REST API path,
 *       consumed by notification-service).</li>
 * </ul>
 * Both use JSON serialization with String keys.
 */
@Configuration
public class KafkaProducerConfig {

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    // ----------------------------------------------------------------
    // Shared producer config map
    // ----------------------------------------------------------------

    private Map<String, Object> baseProducerConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        config.put(ProducerConfig.ACKS_CONFIG, "all");
        config.put(ProducerConfig.RETRIES_CONFIG, 3);
        config.put(ProducerConfig.RETRY_BACKOFF_MS_CONFIG, 1000);
        config.put(ProducerConfig.BATCH_SIZE_CONFIG, 16384);
        config.put(ProducerConfig.LINGER_MS_CONFIG, 5);
        return config;
    }

    // ----------------------------------------------------------------
    // MessageCreatedEvent pipeline (chat.message-created topic)
    // ----------------------------------------------------------------

    @Bean
    public ProducerFactory<String, MessageCreatedEvent> messageCreatedProducerFactory() {
        return new DefaultKafkaProducerFactory<>(baseProducerConfig());
    }

    @Bean
    public KafkaTemplate<String, MessageCreatedEvent> messageCreatedKafkaTemplate() {
        KafkaTemplate<String, MessageCreatedEvent> template =
                new KafkaTemplate<>(messageCreatedProducerFactory());
        template.setDefaultTopic("chat.message-created");
        return template;
    }

    // ----------------------------------------------------------------
    // MessageEvent pipeline (message-events topic)
    // ----------------------------------------------------------------

    @Bean
    public ProducerFactory<String, MessageEvent> messageEventProducerFactory() {
        return new DefaultKafkaProducerFactory<>(baseProducerConfig());
    }

    @Bean
    public KafkaTemplate<String, MessageEvent> messageEventKafkaTemplate() {
        KafkaTemplate<String, MessageEvent> template =
                new KafkaTemplate<>(messageEventProducerFactory());
        template.setDefaultTopic("message-events");
        return template;
    }
}
