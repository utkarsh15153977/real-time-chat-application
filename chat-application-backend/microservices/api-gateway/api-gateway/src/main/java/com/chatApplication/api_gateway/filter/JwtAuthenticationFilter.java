package com.chatApplication.api_gateway.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Slf4j
@Component
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    private final ReactiveStringRedisTemplate redisTemplate;

    @Value("${jwt.secret:defaultSecretKeyThatIsAtLeast32BytesLongForHS256Algorithm!}")
    private String jwtSecret;

    @Value("${jwt.jwk-set-uri:}")
    private String jwkSetUri;

    @Value("${internal.secret:defaultInternalSecretKey}")
    private String internalSecret;

    public JwtAuthenticationFilter(ReactiveStringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    private static final List<String> EXCLUDED_PATHS = List.of(
            "/api/auth/",
            "/actuator/",
            "/health",
            "/ws"
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        // 1. Bypass excluded public paths
        if (isExcludedPath(path)) {
            return chain.filter(exchange);
        }

        // 2. Extract token from Authorization header or query parameters
        String token = extractToken(request);
        if (!StringUtils.hasText(token)) {
            return onError(exchange, "Missing authorization token", HttpStatus.UNAUTHORIZED);
        }

        // 3. Check token blacklist in Redis with fail-open handling
        return isTokenBlacklisted(token)
                .flatMap(isBlacklisted -> {
                    if (Boolean.TRUE.equals(isBlacklisted)) {
                        log.warn("Unauthorized attempt with blacklisted token on path: {}", path);
                        return onError(exchange, "Token is blacklisted", HttpStatus.UNAUTHORIZED);
                    }

                    // 4. Validate JWT and extract claims
                    try {
                        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
                        Claims claims = Jwts.parser()
                                .verifyWith(key)
                                .build()
                                .parseSignedClaims(token)
                                .getPayload();

                        String email = claims.getSubject();
                        String userId = claims.get("userId", String.class);

                        if (userId == null) {
                            userId = email;
                        }

                        // 5. Strip spoofed headers and apply validated downstream headers
                        ServerHttpRequest modifiedRequest = request.mutate()
                                .headers(httpHeaders -> {
                                    httpHeaders.remove("X-User-Id");
                                    httpHeaders.remove("X-User-Roles");
                                    httpHeaders.remove("X-User-Email");
                                    httpHeaders.remove("X-Internal-Secret");
                                })
                                .header("X-User-Id", userId)
                                .header("X-User-Email", email)
                                .header("X-Internal-Secret", internalSecret)
                                .build();

                        return chain.filter(exchange.mutate().request(modifiedRequest).build());

                    } catch (Exception e) {
                        log.warn("Invalid JWT token for path {}: {}", path, e.getMessage());
                        return onError(exchange, "Invalid or expired authorization token", HttpStatus.UNAUTHORIZED);
                    }
                });
    }

    private boolean isExcludedPath(String path) {
        return EXCLUDED_PATHS.stream().anyMatch(path::startsWith);
    }

    private String extractToken(ServerHttpRequest request) {
        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (StringUtils.hasText(authHeader) && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }

        if (request.getQueryParams().containsKey("token")) {
            return request.getQueryParams().getFirst("token");
        }

        return null;
    }

    private Mono<Boolean> isTokenBlacklisted(String token) {
        if (redisTemplate == null) {
            return Mono.just(false);
        }

        return redisTemplate.hasKey("blacklist:" + token)
                .onErrorResume(ex -> {
                    log.error("Redis error during blacklist check, allowing request (fail-open)", ex);
                    return Mono.just(false);
                });
    }

    private Mono<Void> onError(ServerWebExchange exchange, String errorMessage, HttpStatus status) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String jsonResponseBody = String.format(
                "{\"timestamp\":%d,\"status\":%d,\"error\":\"%s\",\"message\":\"%s\"}",
                System.currentTimeMillis(),
                status.value(),
                status.getReasonPhrase(),
                errorMessage
        );
        byte[] bytes = jsonResponseBody.getBytes(StandardCharsets.UTF_8);
        return response.writeWith(Mono.just(response.bufferFactory().wrap(bytes)));
    }

    @Override
    public int getOrder() {
        return -1;
    }
}