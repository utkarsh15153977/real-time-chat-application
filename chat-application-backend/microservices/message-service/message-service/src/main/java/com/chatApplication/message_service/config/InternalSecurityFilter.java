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
 * <p>
 * This filter acts as a network perimeter defense, ensuring that
 * message-service only accepts requests from trusted sources
 * (API Gateway and other internal services).
 * <p>
 * Security model:
 *   - API Gateway injects X-Internal-Secret header on all proxied requests
 *   - message-service validates this header against a shared secret
 *   - Requests without valid secret are rejected with 403 Forbidden
 *   - This prevents direct access to message-service bypassing the gateway
 * <p>
 * Exempt paths:
 *   - /actuator/**: Health checks from load balancers/monitoring
 *   - /test/**: Internal testing endpoints
 */
@Slf4j
@Component
public class InternalSecurityFilter extends OncePerRequestFilter {

    @Value("${security.internal-secret:${INTERNAL_SERVICE_SECRET:blinkInternalSecret2024}}")
    private String expectedSecret;

    /** Paths that bypass internal secret validation */
    private static final String[] EXEMPT_PATHS = {
            "/actuator/",
            "/test/"
    };

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();

        // Skip validation for exempt paths
        if (isExemptPath(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        // Validate internal secret header
        String providedSecret = request.getHeader("X-Internal-Secret");

        if (providedSecret == null || providedSecret.isBlank()) {
            log.warn("Internal security violation: missing X-Internal-Secret header from {}",
                    request.getRemoteAddr());
            rejectRequest(response, "Forbidden: missing internal service secret");
            return;
        }

        if (!java.security.MessageDigest.isEqual(
                providedSecret.getBytes(StandardCharsets.UTF_8),
                expectedSecret.getBytes(StandardCharsets.UTF_8))) {
            log.warn("Internal security violation: invalid X-Internal-Secret from {}",
                    request.getRemoteAddr());
            rejectRequest(response, "Forbidden: invalid internal service secret");
            return;
        }

        // Valid secret - proceed
        filterChain.doFilter(request, response);
    }

    /**
     * Checks if the path should bypass internal secret validation.
     */
    private boolean isExemptPath(String path) {
        for (String exempt : EXEMPT_PATHS) {
            if (path.startsWith(exempt)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Rejects the request with 403 Forbidden and JSON body.
     */
    private void rejectRequest(HttpServletResponse response, String message)
            throws IOException {
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        String body = String.format(
                "{\"status\":403,\"error\":\"Forbidden\",\"message\":\"%s\"}",
                message);

        response.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));
        response.getOutputStream().flush();
    }
}
