package com.chatApplication.api_gateway.filter;

import com.chatApplication.api_gateway.config.SecurityLogUtils;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
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
 * - Blacklisted token -> 401 Unauthorized
 * - X-Internal-Secret header -> injected downstream
 * - Filter has correct order
 */
@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    private JwtAuthenticationFilter filter;
    private GatewayFilterChain chain;
    private ReactiveStringRedisTemplate redisTemplate;
    private static final String SECRET = "testSecretKeyForJwtTokenSigningMustBeLongEnoughForHs256!!";

    @BeforeEach
    void setUp() {
        redisTemplate = mock(ReactiveStringRedisTemplate.class);
        filter = new JwtAuthenticationFilter(redisTemplate);
        ReflectionTestUtils.setField(filter, "jwtSecret", SECRET);
        ReflectionTestUtils.setField(filter, "jwkSetUri", "");
        ReflectionTestUtils.setField(filter, "internalSecret", "blinkInternalSecret2024");
        chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());
        when(redisTemplate.hasKey(anyString())).thenReturn(Mono.just(false));
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
        String internalSecret = exchange.getRequest().getHeaders()
                .getFirst("X-Internal-Secret");

        assertThat(downstreamUserId).isEqualTo("123");
        assertThat(downstreamEmail).isEqualTo("user@example.com");
        assertThat(internalSecret).isEqualTo("blinkInternalSecret2024");
    }

    @Test
    @DisplayName("Spoofed X-User-Id header is stripped and replaced")
    void filter_spoofedHeader_isStrippedAndReplaced() {
        String token = generateValidToken("123", "user@example.com");

        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/messages")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .header("X-User-Id", "999")
                .header("X-User-Roles", "ROLE_ADMIN")
                .build();

        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        filter.filter(exchange, chain).block();

        String downstreamUserId = exchange.getRequest().getHeaders()
                .getFirst("X-User-Id");

        assertThat(downstreamUserId).isEqualTo("123");
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
    @DisplayName("Blacklisted token returns 401 Unauthorized")
    void filter_blacklistedToken_returns401() {
        String token = generateValidToken("123", "user@example.com");

        // Mock Redis to return true for blacklist check
        when(redisTemplate.hasKey(anyString())).thenReturn(Mono.just(true));

        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/messages")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .build();

        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Redis failure allows request (fail-open)")
    void filter_redisFailure_allowsRequest() {
        String token = generateValidToken("123", "user@example.com");

        // Mock Redis to throw exception
        when(redisTemplate.hasKey(anyString()))
                .thenReturn(Mono.error(new RuntimeException("Redis unavailable")));

        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/messages")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .build();

        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        filter.filter(exchange, chain).block();

        // Request should proceed (fail-open)
        verify(chain).filter(any());
        String downstreamUserId = exchange.getRequest().getHeaders()
                .getFirst("X-User-Id");
        assertThat(downstreamUserId).isEqualTo("123");
    }

    @Test
    @DisplayName("Filter has highest priority (order -1)")
    void filter_hasCorrectOrder() {
        assertThat(filter.getOrder()).isEqualTo(-1);
    }

    @Test
    @DisplayName("SecurityLogUtils masks tokens correctly")
    void securityLogUtils_masksTokenCorrectly() {
        String longToken = "eyJhbGciOiJIUzI1NiJ9.eyJ1c2VySWQiOiIxMjMifQ.signature";
        String masked = SecurityLogUtils.maskToken(longToken);
        assertThat(masked).startsWith("eyJhbGciOi");
        assertThat(masked).contains("[...MASKED]");
        assertThat(masked).endsWith("ure?");

        // Short token should be fully masked
        assertThat(SecurityLogUtils.maskToken("short")).isEqualTo("[MASKED]");
        assertThat(SecurityLogUtils.maskToken(null)).isEqualTo("[NULL]");
        assertThat(SecurityLogUtils.maskToken("")).isEqualTo("[NULL]");
    }
}
