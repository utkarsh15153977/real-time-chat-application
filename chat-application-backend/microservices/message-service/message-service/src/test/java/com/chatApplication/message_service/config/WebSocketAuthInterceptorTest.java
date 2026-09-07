package com.chatApplication.message_service.config;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

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
 * - CONNECT with blacklisted token -> connection is rejected
 * - Token expiry is stored in session attributes
 */
@ExtendWith(MockitoExtension.class)
class WebSocketAuthInterceptorTest {

    private WebSocketAuthInterceptor interceptor;
    private StringRedisTemplate redisTemplate;
    private WebSocketSessionExpiryManager expiryManager;
    private MessageChannel channel;
    private static final String SECRET = "testSecretKeyForJwtTokenSigningMustBeLongEnoughForHs256!!";

    @BeforeEach
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        expiryManager = mock(WebSocketSessionExpiryManager.class);
        interceptor = new WebSocketAuthInterceptor(redisTemplate, expiryManager);
        ReflectionTestUtils.setField(interceptor, "jwtSecret", SECRET);
        channel = mock(MessageChannel.class);
        lenient().when(redisTemplate.hasKey(anyString())).thenReturn(false);
    }

    private String generateValidToken(String userId, String email) {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        Date now = new Date();
        return Jwts.builder()
                .subject(email)
                .claim("userId", userId)
                .claim("name", "Test User")
                .id("jti-" + userId)
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

        SimpMessageHeaderAccessor resultAccessor = SimpMessageHeaderAccessor.wrap(result);
        assertThat(resultAccessor).isNotNull();
        assertThat(resultAccessor.getUser()).isNotNull();
        assertThat(resultAccessor.getUser().getName()).isEqualTo("123");
        assertThat(resultAccessor.getUser()).isInstanceOf(UsernamePasswordAuthenticationToken.class);

        // Verify token expiry is stored in session attributes
        assertThat(resultAccessor.getSessionAttributes()).containsKey("token_exp");
        assertThat(resultAccessor.getSessionAttributes()).containsKey("user_id");

        // Verify session is registered with expiry manager
        verify(expiryManager).registerSession(eq("session-1"), any(Instant.class));
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

        SimpMessageHeaderAccessor resultAccessor = SimpMessageHeaderAccessor.wrap(result);
        assertThat(resultAccessor).isNotNull();
        assertThat(resultAccessor.getUser()).isNotNull();
        assertThat(resultAccessor.getUser().getName()).isEqualTo("456");

        // Verify session is registered with expiry manager
        verify(expiryManager).registerSession(eq("session-2"), any(Instant.class));
    }

    @Test
    @DisplayName("CONNECT without token rejects connection")
    void preSend_noToken_rejectsConnection() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setSessionId("session-3");

        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        Message<?> result = interceptor.preSend(message, channel);

        SimpMessageHeaderAccessor resultAccessor = SimpMessageHeaderAccessor.wrap(result);
        assertThat(resultAccessor).isNotNull();
        assertThat(resultAccessor.getHeader("error")).isNotNull();
        assertThat((String) resultAccessor.getHeader("error")).contains("Authentication required");
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

        SimpMessageHeaderAccessor resultAccessor = SimpMessageHeaderAccessor.wrap(result);
        assertThat(resultAccessor).isNotNull();
        assertThat(resultAccessor.getHeader("error")).isNotNull();
        assertThat((String) resultAccessor.getHeader("error")).contains("expired");
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

        SimpMessageHeaderAccessor resultAccessor = SimpMessageHeaderAccessor.wrap(result);
        assertThat(resultAccessor).isNotNull();
        assertThat(resultAccessor.getHeader("error")).isNotNull();
    }

    @Test
    @DisplayName("SEND frame passes through without authentication")
    void preSend_sendFrame_passesThrough() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        accessor.setSessionId("session-6");
        accessor.setDestination("/app/chat.sendMessage");

        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        Message<?> result = interceptor.preSend(message, channel);

        assertThat(result).isNotNull();
        SimpMessageHeaderAccessor resultAccessor = SimpMessageHeaderAccessor.wrap(result);
        assertThat(resultAccessor.getUser()).isNull();
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

        SimpMessageHeaderAccessor resultAccessor = SimpMessageHeaderAccessor.wrap(result);
        assertThat(resultAccessor).isNotNull();
        assertThat(resultAccessor.getUser()).isNotNull();
        assertThat(resultAccessor.getUser().getName()).isEqualTo("789");
    }

    @Test
    @DisplayName("CONNECT with blacklisted token rejects connection")
    void preSend_blacklistedToken_rejectsConnection() {
        String token = generateValidToken("123", "user@example.com");

        // Mock Redis to return true for blacklist check
        when(redisTemplate.hasKey("blacklist:jti-123")).thenReturn(true);

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setSessionId("session-8");
        accessor.addNativeHeader("Authorization", "Bearer " + token);

        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        Message<?> result = interceptor.preSend(message, channel);

        SimpMessageHeaderAccessor resultAccessor = SimpMessageHeaderAccessor.wrap(result);
        assertThat(resultAccessor).isNotNull();
        assertThat(resultAccessor.getHeader("error")).isNotNull();
        assertThat((String) resultAccessor.getHeader("error")).contains("revoked");
    }

    @Test
    @DisplayName("Redis failure allows connection (fail-open)")
    void preSend_redisFailure_allowsConnection() {
        String token = generateValidToken("123", "user@example.com");

        // Mock Redis to throw exception
        when(redisTemplate.hasKey(anyString()))
                .thenThrow(new RuntimeException("Redis unavailable"));

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setSessionId("session-9");
        accessor.addNativeHeader("Authorization", "Bearer " + token);

        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        Message<?> result = interceptor.preSend(message, channel);

        SimpMessageHeaderAccessor resultAccessor = SimpMessageHeaderAccessor.wrap(result);
        assertThat(resultAccessor).isNotNull();
        // Connection should proceed (fail-open)
        assertThat(resultAccessor.getUser()).isNotNull();
        assertThat(resultAccessor.getUser().getName()).isEqualTo("123");
    }

    @Test
    @DisplayName("SecurityLogUtils masks tokens correctly")
    void securityLogUtils_masksTokenCorrectly() {
        String longToken = "eyJhbGciOiJIUzI1NiJ9.eyJ1c2VySWQiOiIxMjMifQ.signature";
        String masked = SecurityLogUtils.maskToken(longToken);
        assertThat(masked).startsWith("eyJhbGciOi");
        assertThat(masked).contains("[...MASKED]");

        assertThat(SecurityLogUtils.maskToken("short")).isEqualTo("[MASKED]");
        assertThat(SecurityLogUtils.maskToken(null)).isEqualTo("[NULL]");
    }
}
