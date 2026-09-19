package com.chatApplication.notification_service.kafka;

import com.chatApplication.notification_service.dto.MessageEvent;
import com.chatApplication.notification_service.dto.NotificationEvent;
import com.chatApplication.notification_service.entity.NotificationType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Security tests for Kafka JSON deserialization trusted-package configuration.
 * <p>
 * Verifies that the notification-service consumer only deserializes events from
 * trusted application packages and rejects type metadata referencing
 * untrusted or arbitrary Java classes.
 * <p>
 * Tests the cross-service type mapping: message-service's
 * {@code com.chatApplication.message_service.kafka.MessageEvent} is resolved
 * to notification-service's {@code com.chatApplication.notification_service.dto.MessageEvent}.
 */
@DisplayName("Kafka Deserialization Security (KAN-14)")
class KafkaDeserializationSecurityTest {

    private static final String TRUSTED_PACKAGES =
            "com.chatApplication.notification_service.dto,com.chatApplication.notification_service.entity";

    private static final String TYPE_MAPPING =
            "com.chatApplication.message_service.kafka.MessageEvent:com.chatApplication.notification_service.dto.MessageEvent";

    private static final String TOPIC = "message-events";

    private JsonDeserializer<MessageEvent> consumerDeserializer;
    private JsonSerializer<MessageEvent> producerSerializer;
    private ObjectMapper objectMapper;

    private byte[] toJsonBytes(Object value) {
        try {
            return objectMapper.writeValueAsBytes(value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        producerSerializer = new JsonSerializer<>();

        consumerDeserializer = new JsonDeserializer<>(MessageEvent.class);
        Map<String, Object> configs = new HashMap<>();
        configs.put(JsonDeserializer.TRUSTED_PACKAGES, TRUSTED_PACKAGES);
        configs.put("spring.json.type.mapping", TYPE_MAPPING);
        configs.put(JsonDeserializer.KEY_DEFAULT_TYPE, String.class);
        consumerDeserializer.configure(configs, false);
    }

    @Nested
    @DisplayName("TEST 1 — Valid application event")
    class ValidEventDeserialization {

        @Test
        @DisplayName("should deserialize legitimate MessageEvent from message-service")
        void shouldDeserializeLegitimateEvent() {
            MessageEvent event = MessageEvent.builder()
                    .messageId(42L)
                    .senderId("user-1")
                    .receiverId("user-2")
                    .content("Test message")
                    .status("SENT")
                    .build();

            byte[] valueBytes = producerSerializer.serialize(TOPIC, new RecordHeaders(), event);

            MessageEvent result = consumerDeserializer.deserialize(TOPIC, new RecordHeaders(), valueBytes);

            assertThat(result).isNotNull();
            assertThat(result.getMessageId()).isEqualTo(42L);
            assertThat(result.getSenderId()).isEqualTo("user-1");
            assertThat(result.getReceiverId()).isEqualTo("user-2");
            assertThat(result.getContent()).isEqualTo("Test message");
            assertThat(result.getStatus()).isEqualTo("SENT");
        }

        @Test
        @DisplayName("should deserialize event with type mapping from message-service package")
        void shouldDeserializeViaTypeMapping() {
            MessageEvent event = MessageEvent.builder()
                    .messageId(100L)
                    .senderId("sender")
                    .receiverId("receiver")
                    .content("Cross-service message")
                    .status("DELIVERED")
                    .build();

            byte[] valueBytes = producerSerializer.serialize(TOPIC, new RecordHeaders(), event);

            RecordHeaders headers = new RecordHeaders();
            headers.add(new RecordHeader("__TypeId__",
                    "com.chatApplication.message_service.kafka.MessageEvent"
                            .getBytes(StandardCharsets.UTF_8)));

            MessageEvent result = consumerDeserializer.deserialize(TOPIC, headers, valueBytes);

            assertThat(result).isNotNull();
            assertThat(result.getMessageId()).isEqualTo(100L);
            assertThat(result.getSenderId()).isEqualTo("sender");
            assertThat(result.getReceiverId()).isEqualTo("receiver");
        }

        @Test
        @DisplayName("should deserialize NotificationEvent via trusted dto package")
        void shouldDeserializeNotificationEvent() {
            NotificationEvent event = NotificationEvent.builder()
                    .notificationId(1L)
                    .senderId("user-1")
                    .receiverId("user-2")
                    .title("New Message")
                    .message("Hello!")
                    .notificationType(NotificationType.MESSAGE)
                    .createdAt(LocalDateTime.now())
                    .build();

            JsonSerializer<NotificationEvent> notifSerializer = new JsonSerializer<>();
            byte[] valueBytes = notifSerializer.serialize("notification-events", new RecordHeaders(), event);

            JsonDeserializer<NotificationEvent> notifDeserializer = new JsonDeserializer<>(NotificationEvent.class);
            Map<String, Object> configs = new HashMap<>();
            configs.put(JsonDeserializer.TRUSTED_PACKAGES, TRUSTED_PACKAGES);
            configs.put(JsonDeserializer.KEY_DEFAULT_TYPE, String.class);
            notifDeserializer.configure(configs, false);

            NotificationEvent result = notifDeserializer.deserialize("notification-events", new RecordHeaders(), valueBytes);

            assertThat(result).isNotNull();
            assertThat(result.getNotificationId()).isEqualTo(1L);
            assertThat(result.getTitle()).isEqualTo("New Message");
            assertThat(result.getNotificationType()).isEqualTo(NotificationType.MESSAGE);
        }
    }

    @Nested
    @DisplayName("TEST 2 — Untrusted package rejection")
    class UntrustedPackageRejection {

        @Test
        @DisplayName("should reject type metadata referencing java.util.ArrayList")
        void shouldRejectJavaUtilArrayList() {
            byte[] jsonBytes = toJsonBytes(java.util.List.of("item1", "item2"));

            RecordHeaders headers = new RecordHeaders();
            headers.add(new RecordHeader("__TypeId__",
                    "java.util.ArrayList".getBytes(StandardCharsets.UTF_8)));

            assertThatThrownBy(() ->
                    consumerDeserializer.deserialize(TOPIC, headers, jsonBytes)
            ).isInstanceOf(Exception.class);
        }

        @Test
        @DisplayName("should reject type metadata referencing java.io.File")
        void shouldRejectJavaIoFile() {
            byte[] jsonBytes = toJsonBytes(Map.of("path", "/tmp/test"));

            RecordHeaders headers = new RecordHeaders();
            headers.add(new RecordHeader("__TypeId__",
                    "java.io.File".getBytes(StandardCharsets.UTF_8)));

            assertThatThrownBy(() ->
                    consumerDeserializer.deserialize(TOPIC, headers, jsonBytes)
            ).isInstanceOf(Exception.class);
        }

        @Test
        @DisplayName("should reject type metadata referencing java.util.HashMap")
        void shouldRejectJavaUtilHashMap() {
            Map<String, String> payload = Map.of("key", "value");
            byte[] jsonBytes = toJsonBytes(payload);

            RecordHeaders headers = new RecordHeaders();
            headers.add(new RecordHeader("__TypeId__",
                    "java.util.HashMap".getBytes(StandardCharsets.UTF_8)));

            assertThatThrownBy(() ->
                    consumerDeserializer.deserialize(TOPIC, headers, jsonBytes)
            ).isInstanceOf(Exception.class);
        }

        @Test
        @DisplayName("should reject type metadata referencing com.evil.MaliciousClass")
        void shouldRejectArbitraryPackage() {
            byte[] jsonBytes = toJsonBytes(Map.of("data", "payload"));

            RecordHeaders headers = new RecordHeaders();
            headers.add(new RecordHeader("__TypeId__",
                    "com.evil.MaliciousClass".getBytes(StandardCharsets.UTF_8)));

            assertThatThrownBy(() ->
                    consumerDeserializer.deserialize(TOPIC, headers, jsonBytes)
            ).isInstanceOf(Exception.class);
        }

        @Test
        @DisplayName("should reject type from message-service kafka package without type mapping")
        void shouldRejectUnmappedMessageServiceType() {
            byte[] jsonBytes = toJsonBytes(Map.of("data", "payload"));

            RecordHeaders headers = new RecordHeaders();
            headers.add(new RecordHeader("__TypeId__",
                    "com.chatApplication.message_service.kafka.MessageCreatedEvent"
                            .getBytes(StandardCharsets.UTF_8)));

            assertThatThrownBy(() ->
                    consumerDeserializer.deserialize(TOPIC, headers, jsonBytes)
            ).isInstanceOf(Exception.class);
        }
    }

    @Nested
    @DisplayName("TEST 3 — Wildcard regression")
    class WildcardRegression {

        @Test
        @DisplayName("should NOT trust all packages")
        void shouldNotTrustAllPackages() {
            byte[] jsonBytes = toJsonBytes(Map.of("data", "test"));

            RecordHeaders headers = new RecordHeaders();
            headers.add(new RecordHeader("__TypeId__",
                    "java.lang.Runtime".getBytes(StandardCharsets.UTF_8)));

            assertThatThrownBy(() ->
                    consumerDeserializer.deserialize(TOPIC, headers, jsonBytes)
            ).isInstanceOf(Exception.class);
        }
    }

    @Nested
    @DisplayName("TEST 4 — Unauthorized type metadata")
    class UnauthorizedTypeMetadata {

        @Test
        @DisplayName("should reject javax.script.ScriptEngine (deserialization gadget)")
        void shouldRejectScriptEngine() {
            byte[] jsonBytes = toJsonBytes(Map.of("data", "test"));

            RecordHeaders headers = new RecordHeaders();
            headers.add(new RecordHeader("__TypeId__",
                    "javax.script.ScriptEngine".getBytes(StandardCharsets.UTF_8)));

            assertThatThrownBy(() ->
                    consumerDeserializer.deserialize(TOPIC, headers, jsonBytes)
            ).isInstanceOf(Exception.class);
        }

        @Test
        @DisplayName("should reject java.lang.ProcessBuilder (RCE vector)")
        void shouldRejectProcessBuilder() {
            byte[] jsonBytes = toJsonBytes(Map.of("command", "whoami"));

            RecordHeaders headers = new RecordHeaders();
            headers.add(new RecordHeader("__TypeId__",
                    "java.lang.ProcessBuilder".getBytes(StandardCharsets.UTF_8)));

            assertThatThrownBy(() ->
                    consumerDeserializer.deserialize(TOPIC, headers, jsonBytes)
            ).isInstanceOf(Exception.class);
        }

        @Test
        @DisplayName("should reject java.lang.UNIXProcess (legacy RCE vector)")
        void shouldRejectUnixProcess() {
            byte[] jsonBytes = toJsonBytes(Map.of("data", "test"));

            RecordHeaders headers = new RecordHeaders();
            headers.add(new RecordHeader("__TypeId__",
                    "java.lang.UNIXProcess".getBytes(StandardCharsets.UTF_8)));

            assertThatThrownBy(() ->
                    consumerDeserializer.deserialize(TOPIC, headers, jsonBytes)
            ).isInstanceOf(Exception.class);
        }

        @Test
        @DisplayName("should reject org.springframework.context.ApplicationContext")
        void shouldRejectSpringApplicationContext() {
            byte[] jsonBytes = toJsonBytes(Map.of("data", "test"));

            RecordHeaders headers = new RecordHeaders();
            headers.add(new RecordHeader("__TypeId__",
                    "org.springframework.context.ApplicationContext"
                            .getBytes(StandardCharsets.UTF_8)));

            assertThatThrownBy(() ->
                    consumerDeserializer.deserialize(TOPIC, headers, jsonBytes)
            ).isInstanceOf(Exception.class);
        }
    }

    @Nested
    @DisplayName("TEST 5 — Valid event regression")
    class ValidEventRegression {

        @Test
        @DisplayName("should still correctly produce and consume MessageEvent roundtrip")
        void shouldRoundtripLegitimateEvent() {
            MessageEvent original = MessageEvent.builder()
                    .messageId(999L)
                    .senderId("alice")
                    .receiverId("bob")
                    .content("Roundtrip test")
                    .status("SENT")
                    .build();

            byte[] serialized = producerSerializer.serialize(TOPIC, new RecordHeaders(), original);
            MessageEvent deserialized = consumerDeserializer.deserialize(TOPIC, new RecordHeaders(), serialized);

            assertThat(deserialized).isNotNull();
            assertThat(deserialized.getMessageId()).isEqualTo(original.getMessageId());
            assertThat(deserialized.getSenderId()).isEqualTo(original.getSenderId());
            assertThat(deserialized.getReceiverId()).isEqualTo(original.getReceiverId());
            assertThat(deserialized.getContent()).isEqualTo(original.getContent());
            assertThat(deserialized.getStatus()).isEqualTo(original.getStatus());
        }
    }

    @Nested
    @DisplayName("TEST 6 — Error handling")
    class ErrorHandling {

        @Test
        @DisplayName("should handle malformed JSON safely without container crash")
        void shouldHandleMalformedJsonSafely() {
            byte[] malformedBytes = "{invalid json content}".getBytes(StandardCharsets.UTF_8);

            RecordHeaders headers = new RecordHeaders();
            headers.add(new RecordHeader("__TypeId__",
                    "com.chatApplication.notification_service.dto.MessageEvent"
                            .getBytes(StandardCharsets.UTF_8)));

            assertThatThrownBy(() ->
                    consumerDeserializer.deserialize(TOPIC, headers, malformedBytes)
            ).isInstanceOf(Exception.class);
        }

        @Test
        @DisplayName("should handle empty payload safely")
        void shouldHandleEmptyPayloadSafely() {
            byte[] emptyBytes = new byte[0];

            RecordHeaders headers = new RecordHeaders();
            headers.add(new RecordHeader("__TypeId__",
                    "com.chatApplication.notification_service.dto.MessageEvent"
                            .getBytes(StandardCharsets.UTF_8)));

            assertThatThrownBy(() ->
                    consumerDeserializer.deserialize(TOPIC, headers, emptyBytes)
            ).isInstanceOf(Exception.class);
        }

        @Test
        @DisplayName("should handle null type header gracefully")
        void shouldHandleNullTypeHeaderGracefully() {
            MessageEvent event = MessageEvent.builder()
                    .messageId(1L)
                    .senderId("sender")
                    .receiverId("receiver")
                    .content("content")
                    .status("SENT")
                    .build();

            byte[] valueBytes = producerSerializer.serialize(TOPIC, new RecordHeaders(), event);

            RecordHeaders headers = new RecordHeaders();

            MessageEvent result = consumerDeserializer.deserialize(TOPIC, headers, valueBytes);

            assertThat(result).isNotNull();
            assertThat(result.getMessageId()).isEqualTo(1L);
        }
    }

    @Nested
    @DisplayName("TEST 7 — Configuration regression")
    class ConfigurationRegression {

        @Test
        @DisplayName("trusted packages should not contain wildcard")
        void trustedPackagesShouldNotContainWildcard() {
            assertThat(TRUSTED_PACKAGES).doesNotContain("*");
        }

        @Test
        @DisplayName("trusted packages should only contain notification-service application packages")
        void trustedPackagesShouldOnlyContainApplicationPackages() {
            String[] packages = TRUSTED_PACKAGES.split(",");
            for (String pkg : packages) {
                assertThat(pkg.trim()).startsWith("com.chatApplication.notification_service.");
            }
        }

        @Test
        @DisplayName("type mapping should map message-service types to local DTOs")
        void typeMappingShouldMapToLocals() {
            assertThat(TYPE_MAPPING).contains("com.chatApplication.message_service.kafka.MessageEvent");
            assertThat(TYPE_MAPPING).contains("com.chatApplication.notification_service.dto.MessageEvent");
        }

        @Test
        @DisplayName("should reject any package outside the allowlist")
        void shouldRejectAnyPackageOutsideAllowlist() {
            String[] untrustedPackages = {
                    "com.chatApplication.message_service.kafka",
                    "org.springframework.kafka",
                    "org.apache.kafka",
                    "com.fasterxml.jackson.databind",
                    "java.util",
                    "java.lang"
            };

            for (String untrustedPkg : untrustedPackages) {
                byte[] jsonBytes = toJsonBytes(Map.of("data", "test"));
                RecordHeaders headers = new RecordHeaders();
                headers.add(new RecordHeader("__TypeId__",
                        (untrustedPkg + ".FakeClass").getBytes(StandardCharsets.UTF_8)));

                assertThatThrownBy(() ->
                        consumerDeserializer.deserialize(TOPIC, headers, jsonBytes)
                ).as("Should reject package: %s", untrustedPkg)
                        .isInstanceOf(Exception.class);
            }
        }
    }
}
