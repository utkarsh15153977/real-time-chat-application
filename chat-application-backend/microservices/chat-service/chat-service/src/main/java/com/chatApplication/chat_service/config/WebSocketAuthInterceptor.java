package com.chatApplication.chat_service.config;

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
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
 *   2. If no JWT present, fall back to trusted X-User-Id header from Gateway
 *   3. Check Redis blacklist for revoked tokens
 *   4. Set UsernamePasswordAuthenticationToken as Principal
 *   5. Return null on rejection to prevent STOMP CONNECTED from being sent
 * <p>
 * Rejection mechanism:
 *   - Returning null from preSend() causes clientInboundChannel.send() to
 *     return false, which prevents StompSubProtocolHandler from sending
 *     a CONNECTED frame. The client times out waiting for CONNECTED.
 * <p>
 * Security:
 *   - Prefers JWT over client-supplied X-User-Id (Gateway header is fallback only)
 *   - Never logs full JWT tokens or secrets
 *   - Checks Redis blacklist for revoked tokens
 *   - Tracks token expiry for session expiration
 */
@Slf4j
@Component
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    @Value("${jwt.secret}")
    private String jwtSecret;

    private final StringRedisTemplate redisTemplate;
    private final WebSocketSessionExpiryManager expiryManager;
    private final ConcurrentHashMap<String, Principal> sessionPrincipals =
            new ConcurrentHashMap<>();

    public WebSocketAuthInterceptor(
            StringRedisTemplate redisTemplate,
            WebSocketSessionExpiryManager expiryManager) {
        this.redisTemplate = redisTemplate;
        this.expiryManager = expiryManager;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        if (accessor == null) {
            return message;
        }

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            log.info("STOMP CONNECT received: sessionId={}", accessor.getSessionId());
            log.info("Native headers: {}", accessor.toNativeHeaderMap().keySet());

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
        if (token != null) {
            return authenticateWithJwt(accessor, token);
        }

        String forwardedUserId = accessor.getFirstNativeHeader("X-User-Id");
        if (forwardedUserId != null && !forwardedUserId.isBlank()) {
            String email = accessor.getFirstNativeHeader("X-User-Email");
            setPrincipal(accessor, forwardedUserId, email);
            Instant defaultExpiry = Instant.now().plus(1, ChronoUnit.HOURS);
            storeTokenExpiry(accessor, defaultExpiry);
            expiryManager.registerSession(accessor.getSessionId(), defaultExpiry);
            log.debug("STOMP CONNECT authenticated via Gateway header: userId={}",
                    forwardedUserId);
            return true;
        }

        log.warn("STOMP CONNECT rejected: no token provided");
        return false;
    }

    private boolean authenticateWithJwt(StompHeaderAccessor accessor, String token) {
        try {
            Claims claims = parseAndValidateToken(token);
            String userId = claims.get("userId", String.class);
            String email = claims.getSubject();
            String jti = claims.getId();

            if (userId == null || userId.isBlank()) {
                log.warn("STOMP CONNECT rejected: userId claim missing");
                return false;
            }

            if (isTokenBlacklisted(jti)) {
                log.warn("STOMP CONNECT rejected: token revoked");
                return false;
            }

            Instant tokenExpiry = claims.getExpiration() != null
                    ? claims.getExpiration().toInstant()
                    : Instant.now().plus(1, ChronoUnit.HOURS);

            setPrincipal(accessor, userId, email);
            storeTokenExpiry(accessor, tokenExpiry);
            expiryManager.registerSession(accessor.getSessionId(), tokenExpiry);

            log.debug("STOMP CONNECT authenticated via JWT: userId={}, expiry={}",
                    userId, tokenExpiry);
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
            return false;
        }
    }

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
