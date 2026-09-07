package com.chatApplication.message_service.config;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the WebSocket STOMP authentication interceptor.
 * <p>
 * Tests:
 * - CONNECT with valid JWT -> Principal is set with userId
 * - CONNECT with Gateway X-User-Id header -> trusts the forwarded userId
 * - CONNECT without token -> connection is rejected
 * - CONNECT with expired token -> connection is rejected
 * - CONNECT with tampered token -> connection is rejected
 * - Non-CONNECT frames -> pass through without authentication
 */
@ExtendWith(MockitoExtension.class)
class WebSocketAuthInterceptorTest {

    private WebSocketAuthInterceptor interceptor;
    private MessageChannel channel;
    private static final String SECRET = "testSecretKeyForJwtTokenSigningMustBeLongEnoughForHs256!!";

    @BeforeEach
    void setUp() {
        interceptor = new WebSocketAuthInterceptor();
        ReflectionTestUtils.setField(interceptor, "jwtSecret", SECRET);
        channel = mock(MessageChannel.class);
    }

    private String generateValidToken(String userId, String email) {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        Date now = new Date();
        return Jwts.builder()
                .subject(email)
                .claim("userId", userId)
                .claim("name", "Test User")
                .issuedAt(now)
                .expiration(new Date(now.getTime() + 3600000))
                .signWith(key)
                .compact();
    }

    private String generateExpiredToken(String userId) {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        Date now = new Date();
        return Jwts.builder()
                .subject("user@example.com")
                .claim("userId", userId)
                .issuedAt(new Date(now.getTime() - 7200000))
                .expiration(new Date(now.getTime() - 3600000))
                .signWith(key)
                .compact();
    }

    @Test
    @DisplayName("CONNECT with valid JWT sets Principal with userId")
    void preSend_validJwt_setsPrincipal() {
        String token = generateValidToken("123", "user@example.com");

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setSessionId("session-1");
        accessor.addNativeHeader("Authorization", "Bearer " + token);

        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        Message<?> result = interceptor.preSend(message, channel);

        assertThat(result).isNotNull();

        StompHeaderAccessor resultAccessor = StompHeaderAccessor.getAccessor(result);
        assertThat(resultAccessor).isNotNull();
        assertThat(resultAccessor.getUser()).isNotNull();
        assertThat(resultAccessor.getUser().getName()).isEqualTo("123");
        assertThat(resultAccessor.getUser()).isInstanceOf(UsernamePasswordAuthenticationToken.class);
    }

    @Test
    @DisplayName("CONNECT with Gateway X-User-Id header trusts forwarded userId")
    void preSend_gatewayHeader_trustsForwardedUserId() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setSessionId("session-2");
        accessor.addNativeHeader("X-User-Id", "456");
        accessor.addNativeHeader("X-User-Email", "gateway@example.com");

        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        Message<?> result = interceptor.preSend(message, channel);

        StompHeaderAccessor resultAccessor = StompHeaderAccessor.getAccessor(result);
        assertThat(resultAccessor).isNotNull();
        assertThat(resultAccessor.getUser()).isNotNull();
        assertThat(resultAccessor.getUser().getName()).isEqualTo("456");
    }

    @Test
    @DisplayName("CONNECT without token rejects connection")
    void preSend_noToken_rejectsConnection() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setSessionId("session-3");

        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        Message<?> result = interceptor.preSend(message, channel);

        StompHeaderAccessor resultAccessor = StompHeaderAccessor.getAccessor(result);
        assertThat(resultAccessor).isNotNull();
        assertThat(resultAccessor.getErrorMessage()).isNotNull();
        assertThat(resultAccessor.getErrorMessage()).contains("Authentication required");
    }

    @Test
    @DisplayName("CONNECT with expired token rejects connection")
    void preSend_expiredToken_rejectsConnection() {
        String token = generateExpiredToken("123");

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setSessionId("session-4");
        accessor.addNativeHeader("Authorization", "Bearer " + token);

        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        Message<?> result = interceptor.preSend(message, channel);

        StompHeaderAccessor resultAccessor = StompHeaderAccessor.getAccessor(result);
        assertThat(resultAccessor).isNotNull();
        assertThat(resultAccessor.getErrorMessage()).isNotNull();
        assertThat(resultAccessor.getErrorMessage()).contains("expired");
    }

    @Test
    @DisplayName("CONNECT with tampered token rejects connection")
    void preSend_tamperedToken_rejectsConnection() {
        String token = generateValidToken("123", "user@example.com");
        String tampered = token.substring(0, token.length() - 5) + "XXXXX";

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setSessionId("session-5");
        accessor.addNativeHeader("Authorization", "Bearer " + tampered);

        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        Message<?> result = interceptor.preSend(message, channel);

        StompHeaderAccessor resultAccessor = StompHeaderAccessor.getAccessor(result);
        assertThat(resultAccessor).isNotNull();
        assertThat(resultAccessor.getErrorMessage()).isNotNull();
    }

    @Test
    @DisplayName("SEND frame passes through without authentication")
    void preSend_sendFrame_passesThrough() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        accessor.setSessionId("session-6");
        accessor.setDestination("/app/chat.sendMessage");

        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        Message<?> result = interceptor.preSend(message, channel);

        // Result should be the same message, unchanged
        assertThat(result).isNotNull();
        StompHeaderAccessor resultAccessor = StompHeaderAccessor.getAccessor(result);
        assertThat(resultAccessor.getUser()).isNull(); // No auth attempt on SEND
    }

    @Test
    @DisplayName("CONNECT with access_token header extracts token")
    void preSend_accessTokenHeader_extractsToken() {
        String token = generateValidToken("789", "access@example.com");

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setSessionId("session-7");
        accessor.addNativeHeader("access_token", token);

        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        Message<?> result = interceptor.preSend(message, channel);

        StompHeaderAccessor resultAccessor = StompHeaderAccessor.getAccessor(result);
        assertThat(resultAccessor).isNotNull();
        assertThat(resultAccessor.getUser()).isNotNull();
        assertThat(resultAccessor.getUser().getName()).isEqualTo("789");
    }
}
