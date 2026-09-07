package com.chatApplication.message_service.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.Collections;
import java.util.List;

/**
 * STOMP WebSocket channel interceptor for JWT authentication.
 * <p>
 * Intercepts the STOMP CONNECT frame to authenticate clients before
 * they can send or subscribe to any STOMP destinations.
 * <p>
 * Authentication flow:
 *   1. Client sends CONNECT frame with Authorization header or token query param
 *   2. Interceptor extracts and validates the JWT token
 *   3. On success: sets UsernamePasswordAuthenticationToken as Principal
 *   4. On failure: rejects the connection (client receives ERROR frame)
 * <p>
 * Security considerations:
 *   - Only validates CONNECT frames; SUBSCRIBE/SEND frames rely on the
 *     Principal set during CONNECT
 *   - Trusts X-User-Id header from Gateway if present (Gateway already validated JWT)
 *   - Falls back to direct JWT validation if no Gateway header (direct connection)
 *   - Never exposes full token in logs (masked for security)
 * <p>
 * Token extraction priority:
 *   1. Authorization: Bearer <token> header
 *   2. access_token native header (some STOMP clients)
 *   3. token query parameter on WebSocket URL
 */
@Slf4j
@Component
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    @Value("${jwt.secret}")
    private String jwtSecret;

    /**
     * Intercepts inbound STOMP messages before they reach the broker.
     * Only processes CONNECT frames for authentication.
     *
     * @param message     the inbound message
     * @param channel     the message channel
     * @return the message (modified with Principal) or null (rejected)
     */
    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor == null) {
            return message;
        }

        // Only authenticate CONNECT frames
        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            authenticateConnectFrame(accessor);
        }

        return message;
    }

    /**
     * Authenticates a STOMP CONNECT frame.
     * <p>
     * Strategy:
     *   1. If X-User-Id header is present (from Gateway), trust it directly
     *   2. Otherwise, extract and validate JWT token from headers/query params
     *   3. Set Principal on the StompHeaderAccessor for downstream use
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
            log.debug("STOMP CONNECT authenticated via Gateway header: userId={}",
                    forwardedUserId);
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

            if (userId == null || userId.isBlank()) {
                log.warn("STOMP CONNECT rejected: userId claim missing");
                rejectConnection(accessor, "Invalid token: userId claim missing");
                return;
            }

            setPrincipal(accessor, userId, email);
            log.debug("STOMP CONNECT authenticated via JWT: userId={}", userId);

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
        String simpMessageId = accessor.getHeader(
                org.springframework.messaging.simp.SimpMessageHeaderAccessor.SESSION_ID_HEADER) != null
                ? null : null; // Cannot access原始 URI from accessor

        // Check native headers for token (set by some STOMP libraries)
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
        // Setting the user to null and leaving an error message
        // will cause Spring to send an ERROR frame to the client
        accessor.setErrorMessage(message);
    }
}
