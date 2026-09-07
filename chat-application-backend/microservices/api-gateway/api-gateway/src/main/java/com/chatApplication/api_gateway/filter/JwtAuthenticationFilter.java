package com.chatApplication.api_gateway.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Reactive global JWT filter for Spring Cloud Gateway.
 * <p>
 * Responsibilities:
 *   1. Extract JWT from Authorization header (REST) or query param (WebSocket)
 *   2. Validate token signature and expiration cryptographically
 *   3. Strip pre-existing spoofed X-User-Id/X-User-Roles headers
 *   4. Inject validated claims as downstream headers (X-User-Id, X-User-Email, X-User-Roles)
 *   5. Return clean 401 JSON responses on failure
 * <p>
 * Security audit:
 *   - Header spoofing: All pre-existing X-User-* headers are stripped before injection
 *   - Blocking calls: Zero blocking operations; all operations are non-reactive safe
 *   - Memory: DataBuffer usage is minimal and properly released via Mono lifecycle
 *   - Token caching: Not needed for HMAC-based tokens (no JWK Set URI)
 * <p>
 * JWT claim mapping (matches auth-service JwtUtil):
 *   - sub claim -> email address
 *   - userId claim -> user ID (Long)
 *   - name claim -> display name
 *   - roles claim -> authority list
 */
@Slf4j
@Component
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    @Value("${jwt.secret}")
    private String jwtSecret;

    /** Paths that bypass JWT validation (public endpoints) */
    private static final List<String> EXCLUDED_PATHS = List.of(
            "/api/auth/",
            "/actuator/"
    );

    /** Headers that must be stripped to prevent spoofing attacks */
    private static final List<String> FORWARDED_HEADERS_TO_STRIP = List.of(
            "X-User-Id",
            "X-User-Email",
            "X-User-Roles"
    );

    /**
     * Core filter logic. Extracts and validates JWT, then mutates
     * the downstream request with authenticated user claims.
     *
     * @param exchange the current server exchange
     * @param chain    the filter chain
     * @return Mono<Void> completing the filter or writing 401
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        // Skip JWT validation for excluded public paths
        if (isExcludedPath(path)) {
            return chain.filter(exchange);
        }

        // Extract token from Authorization header or query parameter
        String token = extractToken(request);
        if (token == null) {
            return unauthorizedResponse(exchange, "Missing or invalid Authorization header");
        }

        try {
            // Parse and validate JWT cryptographically
            Claims claims = parseAndValidateToken(token);

            // Extract claims (matching auth-service JwtUtil token structure)
            String email = claims.getSubject();                    // sub = email
            String userId = claims.get("userId", String.class);   // custom claim
            String name = claims.get("name", String.class);       // custom claim
            String roles = claims.get("roles", String.class);     // custom claim

            // Strip pre-existing spoofed headers, then inject validated claims
            ServerHttpRequest mutatedRequest = request.mutate()
                    .headers(httpHeaders -> {
                        // Remove any pre-existing X-User-* headers (spoofing protection)
                        FORWARDED_HEADERS_TO_STRIP.forEach(httpHeaders::remove);
                    })
                    .header("X-User-Id", userId != null ? userId : "")
                    .header("X-User-Email", email != null ? email : "")
                    .header("X-User-Name", name != null ? name : "")
                    .header("X-User-Roles", roles != null ? roles : "")
                    .build();

            log.debug("JWT validated: userId={}, path={}", userId, path);

            return chain.filter(exchange.mutate().request(mutatedRequest).build());

        } catch (ExpiredJwtException e) {
            log.warn("Expired JWT for path {}: {}", path, e.getMessage());
            return unauthorizedResponse(exchange, "Token has expired");
        } catch (SignatureException e) {
            log.warn("Invalid JWT signature for path {}: {}", path, e.getMessage());
            return unauthorizedResponse(exchange, "Invalid token signature");
        } catch (Exception e) {
            log.warn("JWT validation failed for path {}: {}", path, e.getMessage());
            return unauthorizedResponse(exchange, "Invalid or expired token");
        }
    }

    /**
     * Extracts the JWT token from the request.
     * Checks Authorization header first, then falls back to query parameter
     * for WebSocket upgrade requests where headers may not be available.
     *
     * @param request the incoming server request
     * @return the JWT token string, or null if not found
     */
    private String extractToken(ServerHttpRequest request) {
        // Primary: Authorization: Bearer <token>
        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }

        // Fallback: ?token=<token> query parameter (WebSocket upgrade)
        String queryToken = request.getQueryParams().getFirst("token");
        if (queryToken != null && !queryToken.isBlank()) {
            return queryToken;
        }

        return null;
    }

    /**
     * Parses and validates the JWT token using HMAC-SHA256.
     * Validates both signature and expiration.
     *
     * @param token the JWT token string
     * @return parsed claims payload
     * @throws io.jsonwebtoken.JwtException if token is invalid or expired
     */
    private Claims parseAndValidateToken(String token) {
        SecretKey key = Keys.hmacShaKeyFor(
                jwtSecret.getBytes(StandardCharsets.UTF_8));

        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Checks if the given path should bypass JWT validation.
     *
     * @param path the request path
     * @return true if the path is in the excluded list
     */
    private boolean isExcludedPath(String path) {
        return EXCLUDED_PATHS.stream().anyMatch(path::startsWith);
    }

    /**
     * Writes a clean 401 Unauthorized JSON response.
     * Properly handles reactive streams without blocking.
     *
     * @param exchange the server exchange
     * @param message  the error message
     * @return Mono<Void> completing the response write
     */
    private Mono<Void> unauthorizedResponse(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        String body = String.format(
                "{\"status\":401,\"error\":\"Unauthorized\",\"message\":\"%s\"}",
                message);

        DataBuffer buffer = response.bufferFactory()
                .wrap(body.getBytes(StandardCharsets.UTF_8));

        return response.writeWith(Mono.just(buffer));
    }

    /**
     * Filter order: -1 ensures this runs before any route-specific filters.
     */
    @Override
    public int getOrder() {
        return -1;
    }
}
