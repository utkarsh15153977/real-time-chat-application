package com.chatApplication.chat_service.config;

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
import java.security.Principal;
import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

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

    private Message<?> createConnectMessage(StompHeaderAccessor accessor) {
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    @Test
    @DisplayName("TEST 1 — CONNECT without token returns null (rejected)")
    void preSend_noToken_rejectsConnection() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setSessionId("session-1");

        Message<?> result = interceptor.preSend(createConnectMessage(accessor), channel);

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("TEST 2 — CONNECT with invalid JWT returns null (rejected)")
    void preSend_invalidJwt_rejectsConnection() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setSessionId("session-2");
        accessor.addNativeHeader("Authorization", "Bearer invalid.jwt.token");

        Message<?> result = interceptor.preSend(createConnectMessage(accessor), channel);

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("TEST 3 — CONNECT with expired JWT returns null (rejected)")
    void preSend_expiredToken_rejectsConnection() {
        String token = generateExpiredToken("123");

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setSessionId("session-3");
        accessor.addNativeHeader("Authorization", "Bearer " + token);

        Message<?> result = interceptor.preSend(createConnectMessage(accessor), channel);

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("TEST 4 — CONNECT with valid JWT sets Principal with userId")
    void preSend_validJwt_setsPrincipal() {
        String token = generateValidToken("123", "user@example.com");

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setSessionId("session-4");
        accessor.addNativeHeader("Authorization", "Bearer " + token);

        Message<?> result = interceptor.preSend(createConnectMessage(accessor), channel);

        SimpMessageHeaderAccessor resultAccessor = SimpMessageHeaderAccessor.wrap(result);
        assertThat(resultAccessor).isNotNull();
        assertThat(resultAccessor.getUser()).isNotNull();
        assertThat(resultAccessor.getUser().getName()).isEqualTo("123");
        assertThat(resultAccessor.getUser()).isInstanceOf(UsernamePasswordAuthenticationToken.class);

        @SuppressWarnings("unchecked")
        ConcurrentHashMap<String, Principal> sessionPrincipals =
                (ConcurrentHashMap<String, Principal>) (Object)
                ReflectionTestUtils.getField(interceptor, "sessionPrincipals");
        assertThat(sessionPrincipals).containsKey("session-4");
        assertThat(sessionPrincipals.get("session-4").getName()).isEqualTo("123");

        verify(expiryManager).registerSession(eq("session-4"), any(java.time.Instant.class));
    }

    @Test
    @DisplayName("TEST 5 — CONNECT with blacklisted token returns null (rejected)")
    void preSend_blacklistedToken_rejectsConnection() {
        String token = generateValidToken("123", "user@example.com");
        when(redisTemplate.hasKey("blacklist:jti-123")).thenReturn(true);

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setSessionId("session-5");
        accessor.addNativeHeader("Authorization", "Bearer " + token);

        Message<?> result = interceptor.preSend(createConnectMessage(accessor), channel);

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("TEST 6 — CONNECT with spoofed userId and valid JWT still uses JWT identity")
    void preSend_spoofedUserId_usesJwtIdentity() {
        String token = generateValidToken("123", "user@example.com");

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setSessionId("session-6");
        accessor.addNativeHeader("X-User-Id", "999");
        accessor.addNativeHeader("Authorization", "Bearer " + token);

        Message<?> result = interceptor.preSend(createConnectMessage(accessor), channel);

        SimpMessageHeaderAccessor resultAccessor = SimpMessageHeaderAccessor.wrap(result);
        assertThat(resultAccessor).isNotNull();
        assertThat(resultAccessor.getUser()).isNotNull();
        assertThat(resultAccessor.getUser().getName()).isEqualTo("123");
    }

    @Test
    @DisplayName("TEST 7 — Redis failure allows connection (fail-open)")
    void preSend_redisFailure_allowsConnection() {
        String token = generateValidToken("123", "user@example.com");
        when(redisTemplate.hasKey(anyString()))
                .thenThrow(new RuntimeException("Redis unavailable"));

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setSessionId("session-7");
        accessor.addNativeHeader("Authorization", "Bearer " + token);

        Message<?> result = interceptor.preSend(createConnectMessage(accessor), channel);

        SimpMessageHeaderAccessor resultAccessor = SimpMessageHeaderAccessor.wrap(result);
        assertThat(resultAccessor).isNotNull();
        assertThat(resultAccessor.getUser()).isNotNull();
        assertThat(resultAccessor.getUser().getName()).isEqualTo("123");
    }

    @Test
    @DisplayName("TEST 8 — Gateway X-User-Id header is trusted directly")
    void preSend_gatewayHeader_trustsForwardedUserId() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setSessionId("session-8");
        accessor.addNativeHeader("X-User-Id", "456");
        accessor.addNativeHeader("X-User-Email", "gateway@example.com");

        Message<?> result = interceptor.preSend(createConnectMessage(accessor), channel);

        SimpMessageHeaderAccessor resultAccessor = SimpMessageHeaderAccessor.wrap(result);
        assertThat(resultAccessor).isNotNull();
        assertThat(resultAccessor.getUser()).isNotNull();
        assertThat(resultAccessor.getUser().getName()).isEqualTo("456");
        verify(expiryManager).registerSession(eq("session-8"), any(java.time.Instant.class));
    }

    @Test
    @DisplayName("TEST 9 — access_token header extracts token")
    void preSend_accessTokenHeader_extractsToken() {
        String token = generateValidToken("789", "access@example.com");

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setSessionId("session-9");
        accessor.addNativeHeader("access_token", token);

        Message<?> result = interceptor.preSend(createConnectMessage(accessor), channel);

        SimpMessageHeaderAccessor resultAccessor = SimpMessageHeaderAccessor.wrap(result);
        assertThat(resultAccessor).isNotNull();
        assertThat(resultAccessor.getUser()).isNotNull();
        assertThat(resultAccessor.getUser().getName()).isEqualTo("789");
    }

    @Test
    @DisplayName("TEST 10 — token header extracts token")
    void preSend_tokenHeader_extractsToken() {
        String token = generateValidToken("555", "token@example.com");

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setSessionId("session-10");
        accessor.addNativeHeader("token", token);

        Message<?> result = interceptor.preSend(createConnectMessage(accessor), channel);

        SimpMessageHeaderAccessor resultAccessor = SimpMessageHeaderAccessor.wrap(result);
        assertThat(resultAccessor).isNotNull();
        assertThat(resultAccessor.getUser()).isNotNull();
        assertThat(resultAccessor.getUser().getName()).isEqualTo("555");
    }

    @Test
    @DisplayName("TEST 11 — Tampered token returns null (rejected)")
    void preSend_tamperedToken_rejectsConnection() {
        String token = generateValidToken("123", "user@example.com");
        String tampered = token.substring(0, token.length() - 5) + "XXXXX";

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setSessionId("session-11");
        accessor.addNativeHeader("Authorization", "Bearer " + tampered);

        Message<?> result = interceptor.preSend(createConnectMessage(accessor), channel);

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("TEST 12 — Non-CONNECT frames pass through without authentication")
    void preSend_sendFrame_passesThrough() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        accessor.setSessionId("session-12");
        accessor.setDestination("/app/chat.sendMessage");

        Message<?> result = interceptor.preSend(
                MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders()),
                channel);

        assertThat(result).isNotNull();
        SimpMessageHeaderAccessor resultAccessor = SimpMessageHeaderAccessor.wrap(result);
        assertThat(resultAccessor.getUser()).isNull();
    }

    @Test
    @DisplayName("TEST 13 — CONNECT with missing userId claim returns null (rejected)")
    void preSend_missingUserIdClaim_rejectsConnection() {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        Date now = new Date();
        String token = Jwts.builder()
                .subject("user@example.com")
                .issuedAt(now)
                .expiration(new Date(now.getTime() + 3600000))
                .signWith(key)
                .compact();

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setSessionId("session-13");
        accessor.addNativeHeader("Authorization", "Bearer " + token);

        Message<?> result = interceptor.preSend(createConnectMessage(accessor), channel);

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("TEST 14 — SecurityLogUtils masks tokens correctly")
    void securityLogUtils_masksTokenCorrectly() {
        String longToken = "eyJhbGciOiJIUzI1NiJ9.eyJ1c2VySWQiOiIxMjMifQ.signature";
        String masked = SecurityLogUtils.maskToken(longToken);
        assertThat(masked).startsWith("eyJhbGciOi");
        assertThat(masked).contains("[...MASKED]");

        assertThat(SecurityLogUtils.maskToken("short")).isEqualTo("[MASKED]");
        assertThat(SecurityLogUtils.maskToken(null)).isEqualTo("[NULL]");
    }

    @Test
    @DisplayName("TEST 15 — Identity spoofing: JWT User A + X-User-Id User B → Principal is User A")
    void preSend_jwtUserA_spoofedXUserIdUserB_usesJwtIdentity() {
        String token = generateValidToken("111", "userA@example.com");

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setSessionId("session-15");
        accessor.addNativeHeader("X-User-Id", "999");
        accessor.addNativeHeader("X-User-Email", "userB@example.com");
        accessor.addNativeHeader("Authorization", "Bearer " + token);

        Message<?> result = interceptor.preSend(createConnectMessage(accessor), channel);

        SimpMessageHeaderAccessor resultAccessor = SimpMessageHeaderAccessor.wrap(result);
        assertThat(resultAccessor).isNotNull();
        assertThat(resultAccessor.getUser()).isNotNull();
        assertThat(resultAccessor.getUser().getName()).isEqualTo("111");

        @SuppressWarnings("unchecked")
        ConcurrentHashMap<String, Principal> sessionPrincipals =
                (ConcurrentHashMap<String, Principal>) (Object)
                ReflectionTestUtils.getField(interceptor, "sessionPrincipals");
        assertThat(sessionPrincipals.get("session-15").getName()).isEqualTo("111");
    }

    @Test
    @DisplayName("TEST 16 — Identity spoofing: JWT User A + userId native header User B → Principal is User A")
    void preSend_jwtUserA_spoofedUserIdNativeHeader_usesJwtIdentity() {
        String token = generateValidToken("222", "userA@example.com");

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setSessionId("session-16");
        accessor.addNativeHeader("userId", "888");
        accessor.addNativeHeader("Authorization", "Bearer " + token);

        Message<?> result = interceptor.preSend(createConnectMessage(accessor), channel);

        SimpMessageHeaderAccessor resultAccessor = SimpMessageHeaderAccessor.wrap(result);
        assertThat(resultAccessor).isNotNull();
        assertThat(resultAccessor.getUser()).isNotNull();
        assertThat(resultAccessor.getUser().getName()).isEqualTo("222");

        @SuppressWarnings("unchecked")
        ConcurrentHashMap<String, Principal> sessionPrincipals =
                (ConcurrentHashMap<String, Principal>) (Object)
                ReflectionTestUtils.getField(interceptor, "sessionPrincipals");
        assertThat(sessionPrincipals.get("session-16").getName()).isEqualTo("222");
    }

    @Test
    @DisplayName("TEST 17 — Malformed JWT (not a JWT at all) returns null (rejected)")
    void preSend_malformedJwt_rejectsConnection() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setSessionId("session-17");
        accessor.addNativeHeader("Authorization", "Bearer this.is.not.a.jwt");

        Message<?> result = interceptor.preSend(createConnectMessage(accessor), channel);

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("TEST 18 — CONNECT with no JWT and spoofed X-User-Id uses X-User-Id (Gateway path)")
    void preSend_noJwt_spoofedXUserId_usesGatewayHeader() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setSessionId("session-18");
        accessor.addNativeHeader("X-User-Id", "777");
        accessor.addNativeHeader("X-User-Email", "spoofed@example.com");

        Message<?> result = interceptor.preSend(createConnectMessage(accessor), channel);

        SimpMessageHeaderAccessor resultAccessor = SimpMessageHeaderAccessor.wrap(result);
        assertThat(resultAccessor).isNotNull();
        assertThat(resultAccessor.getUser()).isNotNull();
        assertThat(resultAccessor.getUser().getName()).isEqualTo("777");

        @SuppressWarnings("unchecked")
        ConcurrentHashMap<String, Principal> sessionPrincipals =
                (ConcurrentHashMap<String, Principal>) (Object)
                ReflectionTestUtils.getField(interceptor, "sessionPrincipals");
        assertThat(sessionPrincipals.get("session-18").getName()).isEqualTo("777");
    }

    @Test
    @DisplayName("TEST 19 — Non-CONNECT SUBSCRIBE frame passes through with cached Principal")
    void preSend_subscribeFrame_propagatesCachedPrincipal() {
        String token = generateValidToken("333", "sub@example.com");

        StompHeaderAccessor connectAccessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        connectAccessor.setSessionId("session-19");
        connectAccessor.addNativeHeader("Authorization", "Bearer " + token);

        interceptor.preSend(createConnectMessage(connectAccessor), channel);

        StompHeaderAccessor subAccessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        subAccessor.setSessionId("session-19");
        subAccessor.setDestination("/topic/presence");

        Message<?> subResult = interceptor.preSend(
                MessageBuilder.createMessage(new byte[0], subAccessor.getMessageHeaders()),
                channel);

        SimpMessageHeaderAccessor resultAccessor = SimpMessageHeaderAccessor.wrap(subResult);
        assertThat(resultAccessor).isNotNull();
        assertThat(resultAccessor.getUser()).isNotNull();
        assertThat(resultAccessor.getUser().getName()).isEqualTo("333");
    }

    @Test
    @DisplayName("TEST 20 — Non-CONNECT frame with no cached session passes through without Principal")
    void preSend_unknownSession_noPrincipal() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        accessor.setSessionId("unknown-session");
        accessor.setDestination("/app/heartbeat");

        Message<?> result = interceptor.preSend(
                MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders()),
                channel);

        SimpMessageHeaderAccessor resultAccessor = SimpMessageHeaderAccessor.wrap(result);
        assertThat(resultAccessor).isNotNull();
        assertThat(resultAccessor.getUser()).isNull();
    }
}
