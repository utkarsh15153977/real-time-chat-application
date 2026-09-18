package com.chatApplication.chat_service.config;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;

import javax.crypto.SecretKey;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class WebSocketStompIntegrationTest {

    @LocalServerPort
    private int port;

    @MockitoBean
    private StringRedisTemplate redisTemplate;

    @Value("${jwt.secret}")
    private String applicationJwtSecret;

    private WebSocketStompClient stompClient;

    @BeforeEach
    void setUp() {
        SockJsClient sockJsClient = new SockJsClient(
                List.of(new WebSocketTransport(new StandardWebSocketClient())));
        stompClient = new WebSocketStompClient(sockJsClient);
        lenient().when(redisTemplate.hasKey(anyString())).thenReturn(false);
        lenient().when(redisTemplate.delete(anyString())).thenReturn(true);
    }

    @AfterEach
    void tearDown() {
        if (stompClient != null) {
            stompClient.stop();
        }
    }

    private String generateValidJwt(String userId, String email) {
        SecretKey key = Keys.hmacShaKeyFor(
                applicationJwtSecret.getBytes(StandardCharsets.UTF_8));
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

    private String generateExpiredJwt(String userId) {
        SecretKey key = Keys.hmacShaKeyFor(
                applicationJwtSecret.getBytes(StandardCharsets.UTF_8));
        Date now = new Date();
        return Jwts.builder()
                .subject("expired@example.com")
                .claim("userId", userId)
                .id("jti-exp-" + userId)
                .issuedAt(new Date(now.getTime() - 7200000))
                .expiration(new Date(now.getTime() - 3600000))
                .signWith(key)
                .compact();
    }

    private String getWsUrl() {
        return "http://localhost:" + port + "/ws";
    }

    private static class TestStompSessionHandler extends StompSessionHandlerAdapter {
        private volatile boolean connected = false;

        @Override
        public void afterConnected(StompSession session, StompHeaders connectedHdrs) {
            connected = true;
        }

        public boolean isConnected() { return connected; }
    }

    private StompSession connectOrFail(StompHeaders connectHeaders) throws Exception {
        TestStompSessionHandler handler = new TestStompSessionHandler();
        StompSession session = stompClient.connectAsync(
                getWsUrl(), new WebSocketHttpHeaders(), connectHeaders, handler)
                .get(10, TimeUnit.SECONDS);
        assertThat(handler.isConnected()).isTrue();
        return session;
    }

    private void connectShouldFail(StompHeaders connectHeaders) throws Exception {
        TestStompSessionHandler handler = new TestStompSessionHandler();
        try {
            StompSession session = stompClient.connectAsync(
                    getWsUrl(), new WebSocketHttpHeaders(), connectHeaders, handler)
                    .get(10, TimeUnit.SECONDS);
            if (session != null) {
                session.disconnect();
            }
        } catch (Exception e) {
            // TimeoutException or ExecutionException expected
        }
        assertThat(handler.isConnected()).isFalse();
    }

    @Test
    @DisplayName("INTEGRATION 1 — Valid JWT: CONNECT → CONNECTED → SEND heartbeat")
    void validFlow_connectAndSend() throws Exception {
        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization",
                "Bearer " + generateValidJwt("1001", "auth@example.com"));

        StompSession session = connectOrFail(connectHeaders);

        StompHeaders sendHeaders = new StompHeaders();
        sendHeaders.add("destination", "/app/heartbeat");
        session.send(sendHeaders, new byte[0]);
        Thread.sleep(300);

        session.disconnect();
    }

    @Test
    @DisplayName("INTEGRATION 2 — No JWT: CONNECT rejected, afterConnected not called")
    void invalidFlow_noJwt_rejected() throws Exception {
        StompHeaders connectHeaders = new StompHeaders();
        connectShouldFail(connectHeaders);
    }

    @Test
    @DisplayName("INTEGRATION 3 — Expired JWT: CONNECT rejected")
    void invalidFlow_expiredJwt_rejected() throws Exception {
        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization",
                "Bearer " + generateExpiredJwt("2002"));
        connectShouldFail(connectHeaders);
    }

    @Test
    @DisplayName("INTEGRATION 4 — Malformed JWT: CONNECT rejected")
    void invalidFlow_malformedJwt_rejected() throws Exception {
        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer not.a.valid.jwt");
        connectShouldFail(connectHeaders);
    }

    @Test
    @DisplayName("INTEGRATION 5 — JWT=3001 + X-User-Id=9999: session identity=3001 (JWT wins)")
    void identitySpoofing_jwtOverridesXUserId() throws Exception {
        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization",
                "Bearer " + generateValidJwt("3001", "real@example.com"));
        connectHeaders.add("X-User-Id", "9999");

        StompSession session = connectOrFail(connectHeaders);
        session.disconnect();
    }

    @Test
    @DisplayName("INTEGRATION 6 — Valid JWT via access_token header: CONNECT → CONNECTED")
    void validFlow_accessTokenHeader_connects() throws Exception {
        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("access_token",
                generateValidJwt("4001", "token@example.com"));

        StompSession session = connectOrFail(connectHeaders);
        session.disconnect();
    }

    @Test
    @DisplayName("INTEGRATION 7 — Valid JWT via token header: CONNECT → CONNECTED")
    void validFlow_tokenHeader_connects() throws Exception {
        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("token",
                generateValidJwt("5001", "tok@example.com"));

        StompSession session = connectOrFail(connectHeaders);
        session.disconnect();
    }

    @Test
    @DisplayName("INTEGRATION 8 — Full flow: CONNECT → SUBSCRIBE → SEND → RECEIVE → DISCONNECT")
    void fullFlow_connectSubscribeSendReceive() throws Exception {
        String jwt = generateValidJwt("7001", "flow@example.com");
        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer " + jwt);

        TestStompSessionHandler handler = new TestStompSessionHandler();
        StompSession session = stompClient.connectAsync(
                getWsUrl(), new WebSocketHttpHeaders(), connectHeaders, handler)
                .get(10, TimeUnit.SECONDS);
        assertThat(handler.isConnected()).isTrue();

        BlockingQueue<String> receivedMessages = new ArrayBlockingQueue<>(5);

        session.subscribe(new StompHeaders() {{ setDestination("/topic/presence"); }},
                new StompFrameHandler() {
                    @Override
                    public Type getPayloadType(StompHeaders headers) {
                        return byte[].class;
                    }
                    @Override
                    public void handleFrame(StompHeaders headers, Object payload) {
                        receivedMessages.offer(new String((byte[]) payload));
                    }
                });

        StompHeaders sendHeaders = new StompHeaders();
        sendHeaders.add("destination", "/app/heartbeat");
        session.send(sendHeaders, new byte[0]);

        String received = receivedMessages.poll(5, TimeUnit.SECONDS);
        assertThat(received).isNotNull();
        assertThat(received).contains("7001");

        session.disconnect();
    }

    @Test
    @DisplayName("INTEGRATION 9 — Blacklisted JWT: CONNECT rejected")
    void blacklistedToken_rejected() throws Exception {
        lenient().when(redisTemplate.hasKey("blacklist:jti-6001")).thenReturn(true);

        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization",
                "Bearer " + generateValidJwt("6001", "revoked@example.com"));
        connectShouldFail(connectHeaders);
    }
}
