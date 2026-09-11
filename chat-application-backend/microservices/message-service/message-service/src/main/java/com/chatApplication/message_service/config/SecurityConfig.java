package com.chatApplication.message_service.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Security configuration for the message-service.
 * <p>
 * Security layers:
 *   1. InternalSecurityFilter: Validates X-Internal-Secret header
 *   2. SecurityFilterChain: Path-based access control
 *   3. WebSocketAuthInterceptor: STOMP CONNECT frame authentication
 * <p>
 * Trust model:
 *   - REST endpoints: Gateway validates JWT + injects X-User-Id + X-Internal-Secret
 *   - WebSocket STOMP: WebSocketAuthInterceptor validates JWT on CONNECT frame
 *   - Direct requests: Must include valid X-Internal-Secret header
 * <p>
 * Paths:
 *   - /test/**: Internal testing (no auth)
 *   - /actuator/**: Health checks (no auth)
 *   - /ws, /ws/**: WebSocket handshake (authenticated by WebSocketAuthInterceptor)
 *   - All others: require valid X-Internal-Secret header
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final InternalSecurityFilter internalSecurityFilter;

    public SecurityConfig(InternalSecurityFilter internalSecurityFilter) {
        this.internalSecurityFilter = internalSecurityFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http)
            throws Exception {

        http
                .cors(cors -> cors.configurationSource(
                        corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
                .addFilterBefore(internalSecurityFilter,
                        UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        // Internal test endpoints
                        .requestMatchers("/test/**").permitAll()
                        // Actuator endpoints (health checks)
                        .requestMatchers("/actuator/**").permitAll()
                        // Message REST API (Gateway-injected X-User-Id)
                        .requestMatchers("/api/messages/**").permitAll()
                        // Inbox REST API (Gateway-injected X-User-Id)
                        .requestMatchers("/api/v1/inbox/**").permitAll()
                        // Media upload API (Gateway-injected X-User-Id)
                        .requestMatchers("/api/v1/media/**").permitAll()
                        // Device token registration API (Gateway-injected X-User-Id)
                        .requestMatchers("/api/v1/devices/**").permitAll()
                        // Presence REST API (Gateway-injected X-User-Id)
                        .requestMatchers("/api/presence/**").permitAll()
                        // WebSocket handshake - both exact /ws and /ws/**
                        // authenticated by WebSocketAuthInterceptor on STOMP CONNECT
                        .requestMatchers("/ws", "/ws/**").permitAll()
                        // All other requests require authentication
                        .anyRequest().authenticated()
                );

        return http.build();
    }

    /**
     * CORS configuration for WebSocket upgrade and REST endpoints.
     * Must be consistent with WebSocketConfig.setAllowedOriginPatterns("*").
     * Without this, Spring Security's default CORS handling can reject
     * WebSocket upgrade requests that carry an Origin header.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.addAllowedOriginPattern("*");
        config.addAllowedMethod("*");
        config.addAllowedHeader("*");
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source =
                new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
