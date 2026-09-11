package com.chatApplication.message_service.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Servlet filter that validates internal service-to-service communication.
 *
 * <p>This filter acts as a network perimeter defense, ensuring that
 * message-service only accepts requests from trusted sources
 * (API Gateway and other internal services).</p>
 *
 * <p>Security model:</p>
 * <ul>
 *     <li>API Gateway injects X-Internal-Secret header on proxied requests</li>
 *     <li>message-service validates this header against a shared secret</li>
 *     <li>Requests without valid secret are rejected with 403 Forbidden</li>
 *     <li>WebSocket handshake is exempt because authentication happens
 *         during the STOMP CONNECT frame</li>
 * </ul>
 *
 * <p>Exempt paths:</p>
 * <ul>
 *     <li>/actuator/** - Health checks</li>
 *     <li>/test/** - Internal testing endpoints</li>
 *     <li>/ws - WebSocket handshake</li>
 *     <li>/ws/** - WebSocket-related endpoints</li>
 * </ul>
 */
@Slf4j
@Component
public class InternalSecurityFilter extends OncePerRequestFilter {

    @Value("${security.internal-secret:${INTERNAL_SERVICE_SECRET:blinkInternalSecret2024}}")
    private String expectedSecret;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {

        log.info(
                "Incoming request: method={}, path={}, upgrade={}, origin={}",
                request.getMethod(),
                request.getRequestURI(),
                request.getHeader("Upgrade"),
                request.getHeader("Origin")
        );

        String path = request.getRequestURI();

        /*
         * WebSocket handshake must be allowed through.
         *
         * Authentication happens later when Spring receives
         * the STOMP CONNECT frame and WebSocketAuthInterceptor
         * validates the JWT.
         */
        if (isExemptPath(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        // Validate internal service secret for normal HTTP requests
        String providedSecret = request.getHeader("X-Internal-Secret");

        if (providedSecret == null || providedSecret.isBlank()) {
            log.warn(
                    "Internal security violation: missing X-Internal-Secret header from {}",
                    request.getRemoteAddr()
            );

            rejectRequest(
                    response,
                    "Forbidden: missing internal service secret"
            );
            return;
        }

        if (!java.security.MessageDigest.isEqual(
                providedSecret.getBytes(StandardCharsets.UTF_8),
                expectedSecret.getBytes(StandardCharsets.UTF_8))) {

            log.warn(
                    "Internal security violation: invalid X-Internal-Secret from {}",
                    request.getRemoteAddr()
            );

            rejectRequest(
                    response,
                    "Forbidden: invalid internal service secret"
            );
            return;
        }

        // Valid internal secret - continue processing
        filterChain.doFilter(request, response);
    }

    /**
     * Determines whether the request should bypass
     * internal service-secret validation.
     */
    private boolean isExemptPath(String path) {

        return path.equals("/ws")
                || path.startsWith("/ws/")
                || path.startsWith("/actuator/")
                || path.startsWith("/test/");
    }

    /**
     * Rejects the request with 403 Forbidden.
     */
    private void rejectRequest(
            HttpServletResponse response,
            String message)
            throws IOException {

        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        String body = String.format(
                "{\"status\":403,\"error\":\"Forbidden\",\"message\":\"%s\"}",
                message
        );

        response.getOutputStream()
                .write(body.getBytes(StandardCharsets.UTF_8));

        response.getOutputStream().flush();
    }
}