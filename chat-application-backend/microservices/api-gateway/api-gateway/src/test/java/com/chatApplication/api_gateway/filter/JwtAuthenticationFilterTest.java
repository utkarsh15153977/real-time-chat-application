package com.chatApplication.api_gateway.filter;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class JwtAuthenticationFilterTest {

    private JwtAuthenticationFilter filter;
    private GatewayFilterChain chain;
    private static final String SECRET = "defaultSecretKeyThatIsLongEnoughForHs256Algorithm!!";
    private static final String BEARER = "Bearer ";

    @BeforeEach
    void setUp() {
        filter = new JwtAuthenticationFilter();
        try {
            var field = JwtAuthenticationFilter.class.getDeclaredField("jwtSecret");
            field.setAccessible(true);
            field.set(filter, SECRET);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());
    }

    private String createToken(String userId, String email) {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
            .subject(userId)
            .claim("email", email)
            .issuedAt(new Date())
            .expiration(new Date(System.currentTimeMillis() + 3600000))
            .signWith(key)
            .compact();
    }

    @Test
    void filter_withValidToken_shouldPropagateHeaders() {
        String token = createToken("user-123", "test@example.com");
        MockServerHttpRequest request = MockServerHttpRequest
            .get("/api/users/profile")
            .header(HttpHeaders.AUTHORIZATION, BEARER + token)
            .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        filter.filter(exchange, chain).block();

        verify(chain, times(1)).filter(any());
        ServerHttpRequest mutatedRequest = exchange.getRequest();
        assertEquals("user-123", mutatedRequest.getHeaders().getFirst("X-User-Id"));
        assertEquals("test@example.com", mutatedRequest.getHeaders().getFirst("X-User-Email"));
    }

    @Test
    void filter_withMissingAuthHeader_shouldReturn401() {
        MockServerHttpRequest request = MockServerHttpRequest
            .get("/api/users/profile")
            .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        filter.filter(exchange, chain).block();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        verify(chain, never()).filter(any());
    }

    @Test
    void filter_withInvalidToken_shouldReturn401() {
        MockServerHttpRequest request = MockServerHttpRequest
            .get("/api/users/profile")
            .header(HttpHeaders.AUTHORIZATION, BEARER + "invalid-token")
            .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        filter.filter(exchange, chain).block();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        verify(chain, never()).filter(any());
    }

    @Test
    void filter_withExcludedPath_shouldSkipAuth() {
        MockServerHttpRequest request = MockServerHttpRequest
            .get("/api/auth/login")
            .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        filter.filter(exchange, chain).block();

        verify(chain, times(1)).filter(any());
    }

    @Test
    void filter_withActuatorPath_shouldSkipAuth() {
        MockServerHttpRequest request = MockServerHttpRequest
            .get("/actuator/health")
            .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        filter.filter(exchange, chain).block();

        verify(chain, times(1)).filter(any());
    }

    @Test
    void filter_withWebSocketPath_shouldSkipAuth() {
        MockServerHttpRequest request = MockServerHttpRequest
            .get("/ws/chat")
            .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        filter.filter(exchange, chain).block();

        verify(chain, times(1)).filter(any());
    }
}
