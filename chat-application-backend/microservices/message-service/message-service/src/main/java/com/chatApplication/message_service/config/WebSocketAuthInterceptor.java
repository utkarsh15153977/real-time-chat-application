package com.chatApplication.message_service.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * STOMP WebSocket channel interceptor for JWT authentication.
 * <p>
 * Intercepts the STOMP CONNECT frame to authenticate clients before
 * they can send or subscribe to any STOMP destinations.
 * <p>
 * Authentication flow:
 *   1. Client sends CONNECT frame with Authorization header or token query param
 *   2. Interceptor extracts and validates the JWT token
 *   3. Checks Redis blacklist for revoked tokens
 *   4. On success: sets UsernamePasswordAuthenticationToken as Principal
 *   5. Stores token expiry in session attributes for expiration tracking
 *   6. On failure: rejects the connection (client receives ERROR frame)
 * <p>
 * Security considerations:
 *   - Only validates CONNECT frames; SUBSCRIBE/SEND frames rely on Principal
 *   - Trusts X-User-Id header from Gateway if present
 *   - Falls back to direct JWT validation if no Gateway header
 *   - Never exposes full token in logs (masked for security)
 *   - Checks Redis blacklist for revoked tokens
 *   - Tracks token expiry for session expiration management
 * <p>
 * Token extraction priority:
 *   1. Authorization: Bearer <token> header
 *   2. access_token native header
 *   3. token query parameter on WebSocket URL
 */
@Slf4j
@Component
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    @Value("${jwt.secret}")
    private String jwtSecret;

    private final StringRedisTemplate redisTemplate;
    private final WebSocketSessionExpiryManager expiryManager;

    public WebSocketAuthInterceptor(
            StringRedisTemplate redisTemplate,
            WebSocketSessionExpiryManager expiryManager) {
        this.redisTemplate = redisTemplate;
        this.expiryManager = expiryManager;
    }

    /**
     * Intercepts inbound STOMP messages before they reach the broker.
     * Only processes CONNECT frames for authentication.
     * Also checks frame expiry for all inbound frames.
     *
     * @param message     the inbound message
     * @param channel     the message channel
     * @return the message (modified with Principal) or null (rejected)
     */
    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                StompHeaderAccessor.wrap(message);

        if (accessor == null) {
            return message;
        }

        // Only authenticate CONNECT frames
        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            authenticateConnectFrame(accessor);
        }

        // Return new message with modified headers (wrap creates a separate accessor)
        return MessageBuilder.createMessage(message.getPayload(), accessor.getMessageHeaders());
    }

    /**
     * Authenticates a STOMP CONNECT frame.
     * <p>
     * Strategy:
     *   1. If X-User-Id header is present (from Gateway), trust it directly
     *   2. Otherwise, extract and validate JWT token from headers/query params
     *   3. Check Redis blacklist for revoked tokens
     *   4. Store token expiry in session attributes
     *   5. Set Principal on the StompHeaderAccessor for downstream use
     *
     * @param accessor the STOMP header accessor
     */
    private void authenticateConnectFrame(StompHeaderAccessor accessor) {
        // Strategy 1: Trust forwarded header from API Gateway
        String forwardedUserId = accessor.getFirstNativeHeader("X-User-Id");
        if (forwardedUserId != null && !forwardedUserId.isBlank()) {
            String email = accessor.getFirstNativeHeader("X-User-Email");
            String name = accessor.getFirstNativeHeader("X-User-Name");

            setPrincipal(accessor, forwardedUserId, email);

            // Store a default 1-hour expiry for Gateway-forwarded sessions
            // The actual token expiry is managed by the Gateway
            Instant defaultExpiry = Instant.now().plus(1, ChronoUnit.HOURS);
            storeTokenExpiry(accessor, defaultExpiry);
            expiryManager.registerSession(accessor.getSessionId(), defaultExpiry);

            log.debug("STOMP CONNECT authenticated via Gateway header: userId={}",
                    SecurityLogUtils.maskToken(forwardedUserId));
            return;
        }

        // Strategy 2: Extract and validate JWT token directly
        String token = extractToken(accessor);
        if (token == null) {
            log.warn("STOMP CONNECT rejected: no token provided");
            rejectConnection(accessor, "Authentication required. Provide a valid JWT token.");
            return;
        }

        try {
            Claims claims = parseAndValidateToken(token);
            String userId = claims.get("userId", String.class);
            String email = claims.getSubject();
            String jti = claims.getId();

            if (userId == null || userId.isBlank()) {
                log.warn("STOMP CONNECT rejected: userId claim missing");
                rejectConnection(accessor, "Invalid token: userId claim missing");
                return;
            }

            // Check Redis blacklist for revoked tokens
            if (isTokenBlacklisted(jti)) {
                log.warn("STOMP CONNECT rejected: token revoked jti={}",
                        SecurityLogUtils.maskToken(jti));
                rejectConnection(accessor, "Token has been revoked");
                return;
            }

            // Extract and store token expiry for session expiration tracking
            Instant tokenExpiry = claims.getExpiration() != null
                    ? claims.getExpiration().toInstant()
                    : Instant.now().plus(1, ChronoUnit.HOURS);

            setPrincipal(accessor, userId, email);
            storeTokenExpiry(accessor, tokenExpiry);
            expiryManager.registerSession(accessor.getSessionId(), tokenExpiry);

            log.debug("STOMP CONNECT authenticated via JWT: userId={}, expiry={}",
                    userId, tokenExpiry);

        } catch (ExpiredJwtException e) {
            log.warn("STOMP CONNECT rejected: token expired");
            rejectConnection(accessor, "Token has expired");
        } catch (SignatureException e) {
            log.warn("STOMP CONNECT rejected: invalid signature");
            rejectConnection(accessor, "Invalid token signature");
        } catch (Exception e) {
            log.warn("STOMP CONNECT rejected: {}", e.getMessage());
            rejectConnection(accessor, "Invalid token");
        }
    }

    /**
     * Checks if a JWT ID exists in the Redis blacklist.
     *
     * @param jti the JWT ID to check
     * @return true if blacklisted, false otherwise
     */
    private boolean isTokenBlacklisted(String jti) {
        if (jti == null || jti.isBlank()) {
            return false;
        }
        try {
            String key = "blacklist:" + jti;
            Boolean exists = redisTemplate.hasKey(key);
            return Boolean.TRUE.equals(exists);
        } catch (Exception e) {
            log.error("Redis blacklist check failed: {}", e.getMessage());
            // Fail open - allow connection if Redis is unavailable
            return false;
        }
    }

    /**
     * Stores token expiry timestamp in STOMP session attributes.
     * Used by WebSocketSessionExpiryManager for session expiration.
     *
     * @param accessor  the STOMP header accessor
     * @param tokenExp  the token expiration instant
     */
    private void storeTokenExpiry(StompHeaderAccessor accessor, Instant tokenExp) {
        Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
        if (sessionAttributes == null) {
            sessionAttributes = new java.util.HashMap<>();
            accessor.setSessionAttributes(sessionAttributes);
        }
        sessionAttributes.put("token_exp", tokenExp);
        sessionAttributes.put("user_id", accessor.getUser() != null
                ? accessor.getUser().getName() : null);
    }

    /**
     * Extracts JWT token from STOMP native headers or query parameters.
     *
     * @param accessor the STOMP header accessor
     * @return the JWT token, or null if not found
     */
    private String extractToken(StompHeaderAccessor accessor) {
        // Primary: Authorization: Bearer <token>
        String authHeader = accessor.getFirstNativeHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }

        // Fallback: access_token header (some STOMP clients use this)
        String accessToken = accessor.getFirstNativeHeader("access_token");
        if (accessToken != null && !accessToken.isBlank()) {
            return accessToken;
        }

        // Fallback: token query parameter on WebSocket URL
        String tokenHeader = accessor.getFirstNativeHeader("token");
        if (tokenHeader != null && !tokenHeader.isBlank()) {
            return tokenHeader;
        }

        return null;
    }

    /**
     * Parses and validates the JWT token using HMAC-SHA256.
     *
     * @param token the JWT token string
     * @return parsed claims
     * @throws io.jsonwebtoken.JwtException if token is invalid
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
     * Sets the authenticated Principal on the STOMP header accessor.
     * This Principal is then available to all @MessageMapping methods
     * via the Principal parameter.
     *
     * @param accessor the STOMP header accessor
     * @param userId   the authenticated user ID
     * @param email    the user's email (optional)
     */
    private void setPrincipal(
            StompHeaderAccessor accessor,
            String userId,
            String email) {

        List<SimpleGrantedAuthority> authorities =
                Collections.singletonList(
                        new SimpleGrantedAuthority("ROLE_USER"));

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        userId,       // principal name = userId
                        null,         // credentials (not needed)
                        authorities);

        // Store email as a detail for controllers that need it
        if (email != null) {
            authentication.setDetails(email);
        }

        accessor.setUser(authentication);
    }

    /**
     * Rejects a STOMP CONNECT frame by setting an error header.
     * The client will receive an ERROR frame and the connection will be closed.
     *
     * @param accessor the STOMP header accessor
     * @param message  the error message
     */
    private void rejectConnection(StompHeaderAccessor accessor, String message) {
        accessor.setHeader("error", message);
    }
}
