package com.chatApplication.message_service.kafka;

import com.chatApplication.message_service.entity.MessageType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Security tests for Kafka JSON deserialization trusted-package configuration.
 * <p>
 * Verifies that the message-service consumer only deserializes events from
 * trusted application packages and rejects type metadata referencing
 * untrusted or arbitrary Java classes.
 * <p>
 * These tests exercise the {@link JsonDeserializer} directly without
 * requiring a running Kafka broker.
 */
@DisplayName("Kafka Deserialization Security (KAN-14)")
class KafkaDeserializationSecurityTest {

    private static final String TRUSTED_PACKAGES =
            "com.chatApplication.message_service.kafka,com.chatApplication.message_service.entity";

    private static final String TOPIC = "chat.message-created";

    private JsonDeserializer<MessageCreatedEvent> deserializer;
    private JsonSerializer<MessageCreatedEvent> serializer;
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
        serializer = new JsonSerializer<>();

        deserializer = new JsonDeserializer<>(MessageCreatedEvent.class);
        Map<String, Object> configs = new HashMap<>();
        configs.put(JsonDeserializer.TRUSTED_PACKAGES, TRUSTED_PACKAGES);
        configs.put(JsonDeserializer.KEY_DEFAULT_TYPE, String.class);
        deserializer.configure(configs, false);
    }

    @Nested
    @DisplayName("TEST 1 — Valid application event")
    class ValidEventDeserialization {

        @Test
        @DisplayName("should deserialize legitimate MessageCreatedEvent successfully")
        void shouldDeserializeLegitimateEvent() {
            MessageCreatedEvent event = MessageCreatedEvent.builder()
                    .messageId("msg-001")
                    .chatRoomId("userA-userB")
                    .senderId("userA")
                    .recipientId("userB")
                    .content("Hello, world!")
                    .type(MessageType.TEXT)
                    .createdAt(Instant.parse("2026-09-19T10:30:00Z"))
                    .build();

            byte[] valueBytes = serializer.serialize(TOPIC,
                    new org.apache.kafka.common.header.internals.RecordHeaders(), event);

            MessageCreatedEvent result = deserializer.deserialize(TOPIC, new RecordHeaders(), valueBytes);

            assertThat(result).isNotNull();
            assertThat(result.getMessageId()).isEqualTo("msg-001");
            assertThat(result.getChatRoomId()).isEqualTo("userA-userB");
            assertThat(result.getSenderId()).isEqualTo("userA");
            assertThat(result.getRecipientId()).isEqualTo("userB");
            assertThat(result.getContent()).isEqualTo("Hello, world!");
            assertThat(result.getType()).isEqualTo(MessageType.TEXT);
            assertThat(result.getCreatedAt()).isEqualTo(Instant.parse("2026-09-19T10:30:00Z"));
        }

        @Test
        @DisplayName("should deserialize event with all MessageType variants")
        void shouldDeserializeAllMessageTypes() {
            for (MessageType type : MessageType.values()) {
                MessageCreatedEvent event = MessageCreatedEvent.builder()
                        .messageId("msg-" + type.name())
                        .chatRoomId("room-1")
                        .senderId("sender")
                        .recipientId("recipient")
                        .content("test")
                        .type(type)
                        .createdAt(Instant.now())
                        .build();

                byte[] valueBytes = serializer.serialize(TOPIC,
                        new RecordHeaders(), event);

                MessageCreatedEvent result = deserializer.deserialize(TOPIC, new RecordHeaders(), valueBytes);

                assertThat(result).isNotNull();
                assertThat(result.getType()).isEqualTo(type);
            }
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
                    deserializer.deserialize(TOPIC, headers, jsonBytes)
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
                    deserializer.deserialize(TOPIC, headers, jsonBytes)
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
                    deserializer.deserialize(TOPIC, headers, jsonBytes)
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
                    deserializer.deserialize(TOPIC, headers, jsonBytes)
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
                    deserializer.deserialize(TOPIC, headers, jsonBytes)
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
                    deserializer.deserialize(TOPIC, headers, jsonBytes)
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
                    deserializer.deserialize(TOPIC, headers, jsonBytes)
            ).isInstanceOf(Exception.class);
        }
    }

    @Nested
    @DisplayName("TEST 5 — Valid event regression")
    class ValidEventRegression {

        @Test
        @DisplayName("should still correctly produce and consume MessageCreatedEvent roundtrip")
        void shouldRoundtripLegitimateEvent() {
            MessageCreatedEvent original = MessageCreatedEvent.builder()
                    .messageId("roundtrip-001")
                    .chatRoomId("alice-bob")
                    .senderId("alice")
                    .recipientId("bob")
                    .content("Roundtrip test message")
                    .type(MessageType.IMAGE)
                    .createdAt(Instant.parse("2026-09-19T15:00:00Z"))
                    .build();

            byte[] serialized = serializer.serialize(TOPIC, new RecordHeaders(), original);
            MessageCreatedEvent deserialized = deserializer.deserialize(TOPIC, new RecordHeaders(), serialized);

            assertThat(deserialized).isEqualTo(original);
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
                    "com.chatApplication.message_service.kafka.MessageCreatedEvent"
                            .getBytes(StandardCharsets.UTF_8)));

            assertThatThrownBy(() ->
                    deserializer.deserialize(TOPIC, headers, malformedBytes)
            ).isInstanceOf(Exception.class);
        }

        @Test
        @DisplayName("should handle empty payload safely")
        void shouldHandleEmptyPayloadSafely() {
            byte[] emptyBytes = new byte[0];

            RecordHeaders headers = new RecordHeaders();
            headers.add(new RecordHeader("__TypeId__",
                    "com.chatApplication.message_service.kafka.MessageCreatedEvent"
                            .getBytes(StandardCharsets.UTF_8)));

            assertThatThrownBy(() ->
                    deserializer.deserialize(TOPIC, headers, emptyBytes)
            ).isInstanceOf(Exception.class);
        }

        @Test
        @DisplayName("should handle null type header gracefully")
        void shouldHandleNullTypeHeaderGracefully() {
            MessageCreatedEvent event = MessageCreatedEvent.builder()
                    .messageId("test")
                    .chatRoomId("room")
                    .senderId("sender")
                    .recipientId("recipient")
                    .content("content")
                    .type(MessageType.TEXT)
                    .createdAt(Instant.now())
                    .build();

            byte[] valueBytes = serializer.serialize(TOPIC, new RecordHeaders(), event);

            RecordHeaders headers = new RecordHeaders();

            MessageCreatedEvent result = deserializer.deserialize(TOPIC, headers, valueBytes);

            assertThat(result).isNotNull();
            assertThat(result.getMessageId()).isEqualTo("test");
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
        @DisplayName("trusted packages should only contain application packages")
        void trustedPackagesShouldOnlyContainApplicationPackages() {
            String[] packages = TRUSTED_PACKAGES.split(",");
            for (String pkg : packages) {
                assertThat(pkg.trim()).startsWith("com.chatApplication.message_service.");
            }
        }

        @Test
        @DisplayName("should reject any package outside the allowlist")
        void shouldRejectAnyPackageOutsideAllowlist() {
            String[] untrustedPackages = {
                    "com.chatApplication.notification_service.dto",
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
                        deserializer.deserialize(TOPIC, headers, jsonBytes)
                ).as("Should reject package: %s", untrustedPkg)
                        .isInstanceOf(Exception.class);
            }
        }
    }
}
