package com.chatApplication.notification_service.config;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
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
import static org.mockito.Mockito.mock;

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
    }

    @Test
    @DisplayName("TEST 5 — CONNECT with spoofed X-User-Id and valid JWT still uses JWT identity")
    void preSend_spoofedXUserId_usesJwtIdentity() {
        String token = generateValidToken("123", "user@example.com");

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setSessionId("session-5");
        accessor.addNativeHeader("X-User-Id", "999");
        accessor.addNativeHeader("Authorization", "Bearer " + token);

        Message<?> result = interceptor.preSend(createConnectMessage(accessor), channel);

        SimpMessageHeaderAccessor resultAccessor = SimpMessageHeaderAccessor.wrap(result);
        assertThat(resultAccessor).isNotNull();
        assertThat(resultAccessor.getUser()).isNotNull();
        assertThat(resultAccessor.getUser().getName()).isEqualTo("123");
    }

    @Test
    @DisplayName("TEST 6 — CONNECT with spoofed native userId header and valid JWT uses JWT identity")
    void preSend_spoofedNativeUserId_usesJwtIdentity() {
        String token = generateValidToken("123", "user@example.com");

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setSessionId("session-6");
        accessor.addNativeHeader("userId", "999");
        accessor.addNativeHeader("Authorization", "Bearer " + token);

        Message<?> result = interceptor.preSend(createConnectMessage(accessor), channel);

        SimpMessageHeaderAccessor resultAccessor = SimpMessageHeaderAccessor.wrap(result);
        assertThat(resultAccessor).isNotNull();
        assertThat(resultAccessor.getUser()).isNotNull();
        assertThat(resultAccessor.getUser().getName()).isEqualTo("123");
    }

    @Test
    @DisplayName("TEST 7 — access_token header extracts token")
    void preSend_accessTokenHeader_extractsToken() {
        String token = generateValidToken("789", "access@example.com");

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setSessionId("session-7");
        accessor.addNativeHeader("access_token", token);

        Message<?> result = interceptor.preSend(createConnectMessage(accessor), channel);

        SimpMessageHeaderAccessor resultAccessor = SimpMessageHeaderAccessor.wrap(result);
        assertThat(resultAccessor).isNotNull();
        assertThat(resultAccessor.getUser()).isNotNull();
        assertThat(resultAccessor.getUser().getName()).isEqualTo("789");
    }

    @Test
    @DisplayName("TEST 8 — token header extracts token")
    void preSend_tokenHeader_extractsToken() {
        String token = generateValidToken("555", "token@example.com");

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setSessionId("session-8");
        accessor.addNativeHeader("token", token);

        Message<?> result = interceptor.preSend(createConnectMessage(accessor), channel);

        SimpMessageHeaderAccessor resultAccessor = SimpMessageHeaderAccessor.wrap(result);
        assertThat(resultAccessor).isNotNull();
        assertThat(resultAccessor.getUser()).isNotNull();
        assertThat(resultAccessor.getUser().getName()).isEqualTo("555");
    }

    @Test
    @DisplayName("TEST 9 — Tampered token returns null (rejected)")
    void preSend_tamperedToken_rejectsConnection() {
        String token = generateValidToken("123", "user@example.com");
        String tampered = token.substring(0, token.length() - 5) + "XXXXX";

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setSessionId("session-9");
        accessor.addNativeHeader("Authorization", "Bearer " + tampered);

        Message<?> result = interceptor.preSend(createConnectMessage(accessor), channel);

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("TEST 10 — Non-CONNECT frames pass through without authentication")
    void preSend_sendFrame_passesThrough() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        accessor.setSessionId("session-10");
        accessor.setDestination("/app/chat.sendMessage");

        Message<?> result = interceptor.preSend(
                MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders()),
                channel);

        assertThat(result).isNotNull();
        SimpMessageHeaderAccessor resultAccessor = SimpMessageHeaderAccessor.wrap(result);
        assertThat(resultAccessor.getUser()).isNull();
    }

    @Test
    @DisplayName("TEST 11 — CONNECT with missing userId claim returns null (rejected)")
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
        accessor.setSessionId("session-11");
        accessor.addNativeHeader("Authorization", "Bearer " + token);

        Message<?> result = interceptor.preSend(createConnectMessage(accessor), channel);

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("TEST 12 — Identity spoofing: JWT User A + X-User-Id User B → Principal is User A")
    void preSend_jwtUserA_spoofedXUserIdUserB_usesJwtIdentity() {
        String token = generateValidToken("111", "userA@example.com");

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setSessionId("session-12");
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
        assertThat(sessionPrincipals.get("session-12").getName()).isEqualTo("111");
    }

    @Test
    @DisplayName("TEST 13 — Identity spoofing: JWT User A + native userId User B → Principal is User A")
    void preSend_jwtUserA_spoofedUserIdNativeHeader_usesJwtIdentity() {
        String token = generateValidToken("222", "userA@example.com");

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setSessionId("session-13");
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
        assertThat(sessionPrincipals.get("session-13").getName()).isEqualTo("222");
    }

    @Test
    @DisplayName("TEST 14 — Malformed JWT (not a JWT at all) returns null (rejected)")
    void preSend_malformedJwt_rejectsConnection() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setSessionId("session-14");
        accessor.addNativeHeader("Authorization", "Bearer this.is.not.a.jwt");

        Message<?> result = interceptor.preSend(createConnectMessage(accessor), channel);

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("TEST 15 — Non-CONNECT SUBSCRIBE frame passes through with cached Principal")
    void preSend_subscribeFrame_propagatesCachedPrincipal() {
        String token = generateValidToken("333", "sub@example.com");

        StompHeaderAccessor connectAccessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        connectAccessor.setSessionId("session-15");
        connectAccessor.addNativeHeader("Authorization", "Bearer " + token);

        interceptor.preSend(createConnectMessage(connectAccessor), channel);

        StompHeaderAccessor subAccessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        subAccessor.setSessionId("session-15");
        subAccessor.setDestination("/user/queue/notifications");

        Message<?> subResult = interceptor.preSend(
                MessageBuilder.createMessage(new byte[0], subAccessor.getMessageHeaders()),
                channel);

        SimpMessageHeaderAccessor resultAccessor = SimpMessageHeaderAccessor.wrap(subResult);
        assertThat(resultAccessor).isNotNull();
        assertThat(resultAccessor.getUser()).isNotNull();
        assertThat(resultAccessor.getUser().getName()).isEqualTo("333");
    }

    @Test
    @DisplayName("TEST 16 — Non-CONNECT frame with no cached session passes through without Principal")
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

    @Test
    @DisplayName("TEST 17 — CONNECT with empty Authorization header returns null (rejected)")
    void preSend_emptyAuthorizationHeader_rejectsConnection() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setSessionId("session-17");
        accessor.addNativeHeader("Authorization", "");

        Message<?> result = interceptor.preSend(createConnectMessage(accessor), channel);

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("TEST 18 — CONNECT with Bearer prefix but no token returns null (rejected)")
    void preSend_bearerPrefixNoToken_rejectsConnection() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setSessionId("session-18");
        accessor.addNativeHeader("Authorization", "Bearer ");

        Message<?> result = interceptor.preSend(createConnectMessage(accessor), channel);

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("TEST 19 — CONNECT with wrong signing key returns null (rejected)")
    void preSend_wrongSigningKey_rejectsConnection() {
        String wrongSecret = "completelyDifferentSecretKeyForSigningJwtTokens2024!!";
        SecretKey wrongKey = Keys.hmacShaKeyFor(wrongSecret.getBytes(StandardCharsets.UTF_8));
        Date now = new Date();
        String token = Jwts.builder()
                .subject("user@example.com")
                .claim("userId", "123")
                .id("jti-123")
                .issuedAt(now)
                .expiration(new Date(now.getTime() + 3600000))
                .signWith(wrongKey)
                .compact();

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setSessionId("session-19");
        accessor.addNativeHeader("Authorization", "Bearer " + token);

        Message<?> result = interceptor.preSend(createConnectMessage(accessor), channel);

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("TEST 20 — Multiple sessions maintain independent Principals")
    void multipleSessions_maintainIndependentPrincipals() {
        String token1 = generateValidToken("user1", "user1@example.com");
        String token2 = generateValidToken("user2", "user2@example.com");

        StompHeaderAccessor accessor1 = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor1.setSessionId("session-A");
        accessor1.addNativeHeader("Authorization", "Bearer " + token1);

        StompHeaderAccessor accessor2 = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor2.setSessionId("session-B");
        accessor2.addNativeHeader("Authorization", "Bearer " + token2);

        interceptor.preSend(createConnectMessage(accessor1), channel);
        interceptor.preSend(createConnectMessage(accessor2), channel);

        @SuppressWarnings("unchecked")
        ConcurrentHashMap<String, Principal> sessionPrincipals =
                (ConcurrentHashMap<String, Principal>) (Object)
                ReflectionTestUtils.getField(interceptor, "sessionPrincipals");
        assertThat(sessionPrincipals.get("session-A").getName()).isEqualTo("user1");
        assertThat(sessionPrincipals.get("session-B").getName()).isEqualTo("user2");
    }
}
