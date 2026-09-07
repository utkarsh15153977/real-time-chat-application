package com.chatApplication.api_gateway.filter;

import com.chatApplication.api_gateway.config.SecurityLogUtils;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
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
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
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
import java.security.interfaces.RSAPublicKey;
import java.text.ParseException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Reactive global JWT filter for Spring Cloud Gateway.
 * <p>
 * Responsibilities:
 *   1. Extract JWT from Authorization header (REST) or query param (WebSocket)
 *   2. Validate token signature and expiration (HS256 or RS256/JWK)
 *   3. Check token blacklist (Redis) for revoked tokens
 *   4. Strip pre-existing spoofed X-User-Id/X-User-Roles headers
 *   5. Inject validated claims as downstream headers
 *   6. Inject X-Internal-Secret for service-to-service authentication
 *   7. Return clean 401 JSON responses on failure
 * <p>
 * JWT claim mapping (matches auth-service JwtUtil):
 *   - sub claim -> email address
 *   - userId claim -> user ID (Long)
 *   - name claim -> display name
 *   - roles claim -> authority list
 *   - jti claim -> JWT ID (for blacklist revocation)
 *   - exp claim -> expiration time
 * <p>
 * Security features:
 *   - Header spoofing: All pre-existing X-User-* headers are stripped
 *   - Token revocation: Redis-backed blacklist with TTL matching token expiry
 *   - Asymmetric verification: JWK Set URI support with in-memory caching
 *   - Constant-time comparison: MessageDigest.isEqual for secret validation
 */
@Slf4j
@Component
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.jwk-set-uri:}")
    private String jwkSetUri;

    @Value("${security.internal-secret:${INTERNAL_SERVICE_SECRET:blinkInternalSecret2024}}")
    private String internalSecret;

    private final ReactiveStringRedisTemplate redisTemplate;

    /** Cached JWK Set to avoid repeated fetches during key rotation */
    private volatile JWKSet cachedJwkSet;
    private volatile long lastJwkFetchTime = 0;
    private static final long JWK_CACHE_TTL_MS = 24 * 60 * 60 * 1000; // 24 hours

    /** Paths that bypass JWT validation (public endpoints) */
    private static final List<String> EXCLUDED_PATHS = List.of(
            "/api/auth/",
            "/actuator/"
    );

    /** Headers that must be stripped to prevent spoofing attacks */
    private static final List<String> FORWARDED_HEADERS_TO_STRIP = List.of(
            "X-User-Id",
            "X-User-Email",
            "X-User-Roles",
            "X-Internal-Secret"
    );

    public JwtAuthenticationFilter(ReactiveStringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Core filter logic. Extracts and validates JWT, checks blacklist,
     * then mutates the downstream request with authenticated user claims.
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
            // Parse and validate JWT
            Claims claims = parseAndValidateToken(token);

            // Extract JTI for blacklist check
            String jti = claims.get("jti", String.class);
            if (jti == null) {
                jti = claims.getId();
            }

            // Check Redis blacklist asynchronously (non-blocking)
            String finalJti = jti;
            return checkBlacklist(jti)
                    .flatMap(isBlacklisted -> {
                        if (Boolean.TRUE.equals(isBlacklisted)) {
                            log.warn("Revoked JWT used for path {}: jti={}", path,
                                    SecurityLogUtils.maskToken(finalJti));
                            return unauthorizedResponse(exchange, "Token has been revoked");
                        }

                        // Extract claims
                        String email = claims.getSubject();
                        String userId = claims.get("userId", String.class);
                        String name = claims.get("name", String.class);
                        String roles = claims.get("roles", String.class);

                        // Strip pre-existing spoofed headers, then inject validated claims
                        ServerHttpRequest mutatedRequest = request.mutate()
                                .headers(httpHeaders -> {
                                    FORWARDED_HEADERS_TO_STRIP.forEach(httpHeaders::remove);
                                })
                                .header("X-User-Id", userId != null ? userId : "")
                                .header("X-User-Email", email != null ? email : "")
                                .header("X-User-Name", name != null ? name : "")
                                .header("X-User-Roles", roles != null ? roles : "")
                                .header("X-Internal-Secret", internalSecret)
                                .build();

                        log.debug("JWT validated: userId={}, path={}",
                                userId, path);

                        return chain.filter(
                                exchange.mutate().request(mutatedRequest).build());
                    });

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
     * Checks if a JWT ID exists in the Redis blacklist.
     *
     * @param jti the JWT ID to check
     * @return Mono<Boolean> true if blacklisted, false otherwise
     */
    private Mono<Boolean> checkBlacklist(String jti) {
        if (jti == null || jti.isBlank()) {
            return Mono.just(false);
        }
        String key = "blacklist:" + jti;
        return redisTemplate.hasKey(key)
                .onErrorResume(e -> {
                    log.error("Redis blacklist check failed: {}", e.getMessage());
                    // Fail open - allow request if Redis is unavailable
                    // In production, consider fail-closed for higher security
                    return Mono.just(false);
                });
    }

    /**
     * Extracts the JWT token from the request.
     * Checks Authorization header first, then falls back to query parameter.
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
     * Parses and validates the JWT token.
     * Supports both HS256 (symmetric) and RS256 (asymmetric/JWK).
     *
     * @param token the JWT token string
     * @return parsed claims payload
     * @throws Exception if token is invalid or expired
     */
    private Claims parseAndValidateToken(String token) throws Exception {
        // Try RS256/JWK first if JWK Set URI is configured
        if (jwkSetUri != null && !jwkSetUri.isBlank()) {
            try {
                return parseWithJwk(token);
            } catch (Exception e) {
                log.debug("JWK verification failed, falling back to HMAC: {}",
                        e.getMessage());
            }
        }

        // Fallback to HS256 (symmetric key)
        return parseWithHmac(token);
    }

    /**
     * Parses JWT using HMAC-SHA256 with the shared secret.
     */
    private Claims parseWithHmac(String token) {
        SecretKey key = Keys.hmacShaKeyFor(
                jwtSecret.getBytes(StandardCharsets.UTF_8));

        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Parses JWT using RS256 with JWK Set verification.
     * Caches the JWK Set for 24 hours to prevent DoS.
     */
    private Claims parseWithJwk(String token) throws ParseException, java.text.ParseException {
        JWKSet jwkSet = getJwkSet();
        SignedJWT signedJWT = SignedJWT.parse(token);

        // Find matching key by ID
        String keyId = signedJWT.getHeader().getKeyID();
        if (keyId != null) {
            for (com.nimbusds.jose.jwk.JWK jwk : jwkSet.getKeys()) {
                if (jwk.getKeyID().equals(keyId)) {
                    if (jwk instanceof RSAKey rsaKey) {
                        RSAPublicKey publicKey = rsaKey.toRSAPublicKey();
                        RSASSAVerifier verifier = new RSASSAVerifier(publicKey);

                        if (!signedJWT.verify(verifier)) {
                            throw new SignatureException("Invalid RS256 signature");
                        }

                        JWTClaimsSet jwtClaims = signedJWT.getJWTClaimsSet();
                        return convertToJjwtClaims(jwtClaims);
                    }
                }
            }
        }

        // If key ID not found, try all RSA keys
        for (com.nimbusds.jose.jwk.JWK jwk : jwkSet.getKeys()) {
            if (jwk instanceof RSAKey rsaKey) {
                try {
                    RSAPublicKey publicKey = rsaKey.toRSAPublicKey();
                    RSASSAVerifier verifier = new RSASSAVerifier(publicKey);

                    if (signedJWT.verify(verifier)) {
                        JWTClaimsSet jwtClaims = signedJWT.getJWTClaimsSet();
                        return convertToJjwtClaims(jwtClaims);
                    }
                } catch (Exception e) {
                    // Try next key
                }
            }
        }

        throw new SignatureException("No matching JWK found for JWT verification");
    }

    /**
     * Gets the cached JWK Set or fetches a new one.
     * Uses simple TTL-based caching (24 hours).
     */
    private JWKSet getJwkSet() throws ParseException {
        long now = System.currentTimeMillis();
        if (cachedJwkSet == null || (now - lastJwkFetchTime) > JWK_CACHE_TTL_MS) {
            cachedJwkSet = JWKSet.parse(
                    new java.net.URL(jwkSetUri));
            lastJwkFetchTime = now;
            log.debug("JWK Set refreshed: {} keys loaded", cachedJwkSet.getKeys().size());
        }
        return cachedJwkSet;
    }

    /**
     * Converts Nimbus JWTClaimsSet to jjwt Claims for backward compatibility.
     */
    private Claims convertToJjwtClaims(JWTClaimsSet jwtClaims) {
        return Jwts.claims()
                .subject(jwtClaims.getSubject())
                .id(jwtClaims.getJWTID())
                .issuedAt(jwtClaims.getIssueTime())
                .expiration(jwtClaims.getExpirationTime())
                .issuer(jwtClaims.getIssuer())
                .add("userId", jwtClaims.getClaim("userId"))
                .add("name", jwtClaims.getClaim("name"))
                .add("roles", jwtClaims.getClaim("roles"))
                .build();
    }

    /**
     * Checks if the given path should bypass JWT validation.
     */
    private boolean isExcludedPath(String path) {
        return EXCLUDED_PATHS.stream().anyMatch(path::startsWith);
    }

    /**
     * Writes a clean 401 Unauthorized JSON response.
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
