package com.chatApplication.message_service.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Security configuration for the message-service.
 * <p>
 * This service trusts the API Gateway for JWT validation.
 * The Gateway injects validated X-User-Id headers on all proxied requests.
 * <p>
 * Security model:
 *   - REST endpoints: Gateway validates JWT and injects X-User-Id header
 *   - WebSocket STOMP: WebSocketAuthInterceptor validates JWT on CONNECT frame
 *   - This filter chain only enforces path-based access control
 * <p>
 * Paths:
 *   - /test/**: Internal testing endpoints (no auth)
 *   - /api/messages/**: Message REST API (trusted via Gateway)
 *   - /api/v1/media/**: Media upload API (trusted via Gateway)
 *   - /api/presence/**: Presence REST API (trusted via Gateway)
 *   - /ws: WebSocket handshake (authenticated by WebSocketAuthInterceptor)
 *   - All others: require authentication
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http)
            throws Exception {

        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        // Internal test endpoints
                        .requestMatchers("/test/**").permitAll()
                        // Message REST API (Gateway-injected X-User-Id)
                        .requestMatchers("/api/messages/**").permitAll()
                        // Media upload API (Gateway-injected X-User-Id)
                        .requestMatchers("/api/v1/media/**").permitAll()
                        // Presence REST API (Gateway-injected X-User-Id)
                        .requestMatchers("/api/presence/**").permitAll()
                        // WebSocket handshake (authenticated by WebSocketAuthInterceptor)
                        .requestMatchers("/ws/**").permitAll()
                        // Actuator endpoints
                        .requestMatchers("/actuator/**").permitAll()
                        // All other requests require authentication
                        .anyRequest().authenticated()
                );

        return http.build();
    }
}
