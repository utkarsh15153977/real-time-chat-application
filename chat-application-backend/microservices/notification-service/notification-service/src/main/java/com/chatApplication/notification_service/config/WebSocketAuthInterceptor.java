package com.chatApplication.notification_service.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * STOMP WebSocket channel interceptor for JWT authentication.
 * <p>
 * Intercepts the STOMP CONNECT frame to authenticate clients before
 * they can send or subscribe to any STOMP destinations.
 * <p>
 * Authentication flow:
 *   1. Extract and validate JWT from Authorization/access_token/token headers
 *   2. Set UsernamePasswordAuthenticationToken as Principal
 *   3. Return null on rejection to prevent STOMP CONNECTED from being sent
 * <p>
 * Rejection mechanism:
 *   - Returning null from preSend() prevents StompSubProtocolHandler from sending
 *     a CONNECTED frame. The client times out waiting for CONNECTED.
 * <p>
 * Security:
 *   - Principal is derived exclusively from JWT (never from client-supplied headers)
 *   - Never logs full JWT tokens or secrets
 *   - No Redis blacklist check (notification-service does not have Redis)
 */
@Slf4j
@Component
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    @Value("${jwt.secret}")
    private String jwtSecret;

    private final ConcurrentHashMap<String, Principal> sessionPrincipals =
            new ConcurrentHashMap<>();

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        if (accessor == null) {
            return message;
        }

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            log.info("STOMP CONNECT received: sessionId={}", accessor.getSessionId());

            if (!authenticateConnectFrame(accessor)) {
                return null;
            }

            Principal user = accessor.getUser();
            if (user != null && accessor.getSessionId() != null) {
                sessionPrincipals.put(accessor.getSessionId(), user);
                log.info("Cached Principal for session {}: userId={}",
                        accessor.getSessionId(), user.getName());
            }

            Map<String, Object> headerMap = new LinkedHashMap<>(message.getHeaders());
            if (user != null) {
                headerMap.put(SimpMessageHeaderAccessor.USER_HEADER, user);
            }
            return new GenericMessage<>(message.getPayload(), headerMap);
        }

        if (accessor.getSessionId() != null) {
            Principal user = sessionPrincipals.get(accessor.getSessionId());
            if (user != null) {
                Map<String, Object> headerMap =
                        new LinkedHashMap<>(message.getHeaders());
                headerMap.put(SimpMessageHeaderAccessor.USER_HEADER, user);
                return new GenericMessage<>(message.getPayload(), headerMap);
            }
        }

        return message;
    }

    private boolean authenticateConnectFrame(StompHeaderAccessor accessor) {
        String token = extractToken(accessor);
        if (token == null) {
            log.warn("STOMP CONNECT rejected: no token provided");
            return false;
        }
        return authenticateWithJwt(accessor, token);
    }

    private boolean authenticateWithJwt(StompHeaderAccessor accessor, String token) {
        try {
            Claims claims = parseAndValidateToken(token);
            String userId = claims.get("userId", String.class);
            String email = claims.getSubject();

            if (userId == null || userId.isBlank()) {
                log.warn("STOMP CONNECT rejected: userId claim missing");
                return false;
            }

            setPrincipal(accessor, userId, email);

            log.debug("STOMP CONNECT authenticated via JWT: userId={}", userId);
            return true;

        } catch (ExpiredJwtException e) {
            log.warn("STOMP CONNECT rejected: token expired");
            return false;
        } catch (SignatureException e) {
            log.warn("STOMP CONNECT rejected: invalid signature");
            return false;
        } catch (Exception e) {
            log.warn("STOMP CONNECT rejected: {}", e.getMessage());
            return false;
        }
    }

    private String extractToken(StompHeaderAccessor accessor) {
        String authHeader = accessor.getFirstNativeHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }

        String accessToken = accessor.getFirstNativeHeader("access_token");
        if (accessToken != null && !accessToken.isBlank()) {
            return accessToken;
        }

        String tokenHeader = accessor.getFirstNativeHeader("token");
        if (tokenHeader != null && !tokenHeader.isBlank()) {
            return tokenHeader;
        }

        return null;
    }

    private Claims parseAndValidateToken(String token) {
        SecretKey key = Keys.hmacShaKeyFor(
                jwtSecret.getBytes(StandardCharsets.UTF_8));
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private void setPrincipal(
            StompHeaderAccessor accessor,
            String userId,
            String email) {

        List<SimpleGrantedAuthority> authorities =
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"));

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        userId, null, authorities);

        if (email != null) {
            authentication.setDetails(email);
        }

        accessor.setUser(authentication);
    }
}
