package com.chatApplication.api_gateway.filter;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the API Gateway JwtAuthenticationFilter.
 * <p>
 * Tests:
 * - Valid JWT -> downstream headers are set correctly
 * - Expired JWT -> 401 Unauthorized
 * - Tampered JWT -> 401 Unauthorized
 * - Missing token -> 401 Unauthorized
 * - Spoofed X-User-Id header -> stripped and replaced with validated claim
 * - WebSocket token query param -> extracted and validated
 * - Excluded paths -> bypass JWT validation
 */
@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    private JwtAuthenticationFilter filter;
    private GatewayFilterChain chain;
    private static final String SECRET = "testSecretKeyForJwtTokenSigningMustBeLongEnoughForHs256!!";

    @BeforeEach
    void setUp() {
        filter = new JwtAuthenticationFilter();
        ReflectionTestUtils.setField(filter, "jwtSecret", SECRET);
        chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());
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

    private String generateExpiredToken(String userId, String email) {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        Date now = new Date();
        return Jwts.builder()
                .subject(email)
                .claim("userId", userId)
                .issuedAt(new Date(now.getTime() - 7200000))
                .expiration(new Date(now.getTime() - 3600000))
                .signWith(key)
                .compact();
    }

    @Test
    @DisplayName("Valid JWT sets X-User-Id and X-User-Email headers downstream")
    void filter_validJwt_setsDownstreamHeaders() {
        String token = generateValidToken("123", "user@example.com");

        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/messages")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .build();

        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        filter.filter(exchange, chain).block();

        verify(chain).filter(any());

        String downstreamUserId = exchange.getRequest().getHeaders()
                .getFirst("X-User-Id");
        String downstreamEmail = exchange.getRequest().getHeaders()
                .getFirst("X-User-Email");

        assertThat(downstreamUserId).isEqualTo("123");
        assertThat(downstreamEmail).isEqualTo("user@example.com");
    }

    @Test
    @DisplayName("Spoofed X-User-Id header is stripped and replaced")
    void filter_spoofedHeader_isStrippedAndReplaced() {
        String token = generateValidToken("123", "user@example.com");

        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/messages")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .header("X-User-Id", "999") // Spoofed!
                .header("X-User-Roles", "ROLE_ADMIN") // Spoofed!
                .build();

        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        filter.filter(exchange, chain).block();

        String downstreamUserId = exchange.getRequest().getHeaders()
                .getFirst("X-User-Id");
        String downstreamRoles = exchange.getRequest().getHeaders()
                .getFirst("X-User-Roles");

        assertThat(downstreamUserId).isEqualTo("123"); // Not "999"
        assertThat(downstreamRoles).isNull(); // Stripped, not injected
    }

    @Test
    @DisplayName("Expired JWT returns 401 Unauthorized")
    void filter_expiredJwt_returns401() {
        String token = generateExpiredToken("123", "user@example.com");

        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/messages")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .build();

        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);

        verify(chain, never()).filter(any());
    }

    @Test
    @DisplayName("Tampered JWT returns 401 Unauthorized")
    void filter_tamperedJwt_returns401() {
        String token = generateValidToken("123", "user@example.com");
        // Tamper with the token by changing a character
        String tampered = token.substring(0, token.length() - 5) + "XXXXX";

        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/messages")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tampered)
                .build();

        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Missing Authorization header returns 401")
    void filter_missingAuthHeader_returns401() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/messages")
                .build();

        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Token from query parameter is extracted and validated")
    void filter_queryParamToken_isExtracted() {
        String token = generateValidToken("456", "query@example.com");

        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/messages?token=" + token)
                .build();

        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        filter.filter(exchange, chain).block();

        String downstreamUserId = exchange.getRequest().getHeaders()
                .getFirst("X-User-Id");

        assertThat(downstreamUserId).isEqualTo("456");
    }

    @Test
    @DisplayName("Excluded path /api/auth/** bypasses JWT validation")
    void filter_excludedPath_bypassesJwt() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/auth/login")
                .build();

        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        filter.filter(exchange, chain).block();

        // Chain was called without setting 401
        verify(chain).filter(any());
        assertThat(exchange.getResponse().getStatusCode())
                .isNotEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Excluded path /actuator/** bypasses JWT validation")
    void filter_actuatorPath_bypassesJwt() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/actuator/health")
                .build();

        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        filter.filter(exchange, chain).block();

        verify(chain).filter(any());
    }

    @Test
    @DisplayName("Filter has highest priority (order -1)")
    void filter_hasCorrectOrder() {
        assertThat(filter.getOrder()).isEqualTo(-1);
    }
}
