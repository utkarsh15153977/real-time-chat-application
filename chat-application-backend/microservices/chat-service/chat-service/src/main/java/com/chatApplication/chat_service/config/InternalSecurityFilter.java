package com.chatApplication.chat_service.config;

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
 * Validates internal service-to-service communication.
 * Ensures chat-service only accepts requests from trusted sources (API Gateway).
 * Validates X-Internal-Secret header and extracts X-User-Id.
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

        String path = request.getRequestURI();

        if (isExemptPath(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        String providedSecret = request.getHeader("X-Internal-Secret");

        if (providedSecret == null || providedSecret.isBlank()) {
            log.warn(
                    "Internal security violation: missing X-Internal-Secret header from {}",
                    request.getRemoteAddr()
            );
            rejectRequest(response, "Forbidden: missing internal service secret");
            return;
        }

        if (!java.security.MessageDigest.isEqual(
                providedSecret.getBytes(StandardCharsets.UTF_8),
                expectedSecret.getBytes(StandardCharsets.UTF_8))) {

            log.warn(
                    "Internal security violation: invalid X-Internal-Secret from {}",
                    request.getRemoteAddr()
            );
            rejectRequest(response, "Forbidden: invalid internal service secret");
            return;
        }

        String userId = request.getHeader("X-User-Id");
        if (userId == null || userId.isBlank()) {
            log.warn(
                    "Internal security violation: missing X-User-Id header from {}",
                    request.getRemoteAddr()
            );
            rejectRequest(response, "Forbidden: missing user identity header");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean isExemptPath(String path) {
        return path.equals("/ws")
                || path.startsWith("/ws/")
                || path.startsWith("/actuator/")
                || path.startsWith("/swagger-ui/")
                || path.startsWith("/v3/api-docs/");
    }

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
