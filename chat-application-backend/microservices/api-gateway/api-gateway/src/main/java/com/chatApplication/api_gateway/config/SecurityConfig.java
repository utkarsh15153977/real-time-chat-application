package com.chatApplication.api_gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * WebFlux security configuration for the API Gateway.
 * <p>
 * Configures:
 *   - CSRF disabled (stateless API gateway)
 *   - CORS for REST and WebSocket origins
 *   - Path-based authorization rules
 *   - Proper preflight (OPTIONS) handling
 * <p>
 * Note: JWT validation is handled by {@code JwtAuthenticationFilter},
 * not by Spring Security's resource server, to maintain full reactive
 * compatibility with Spring Cloud Gateway.
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    public SecurityWebFilterChain filterChain(ServerHttpSecurity http) {
        http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .cors(cors -> cors.configurationSource(
                        (exchange) -> buildCorsConfiguration()))
                .authorizeExchange(exchange -> exchange
                        // Preflight requests
                        .pathMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        // Auth service (public: register, login)
                        .pathMatchers("/api/auth/**").permitAll()
                        // Actuator (internal monitoring)
                        .pathMatchers("/actuator/**").permitAll()
                        // Health check
                        .pathMatchers("/health").permitAll()
                        // All other requests require JWT validation
                        // (handled by JwtAuthenticationFilter GlobalFilter)
                        .anyExchange().authenticated()
                )
                .exceptionHandling(exceptions -> exceptions
                        // Return 401 for unauthenticated requests instead of redirect
                        .authenticationEntryPoint((exchange, denied) -> {
                            ServerHttpResponse response = exchange.getResponse();
                            response.setStatusCode(HttpStatus.UNAUTHORIZED);
                            response.getHeaders()
                                    .setContentType(
                                            org.springframework.http.MediaType.APPLICATION_JSON);
                            String body = "{\"status\":401,\"error\":\"Unauthorized\","
                                    + "\"message\":\"Authentication required\"}";
                            return response.writeWith(
                                    Mono.just(response.bufferFactory().wrap(
                                            body.getBytes())));
                        })
                );

        return http.build();
    }

    /**
     * Builds CORS configuration for REST and WebSocket origins.
     * Allows the frontend development servers and production domains.
     *
     * @return CorsConfiguration with allowed origins and headers
     */
    private CorsConfiguration buildCorsConfiguration() {
        CorsConfiguration config = new CorsConfiguration();

        config.setAllowedOriginPatterns(List.of(
                "http://localhost:5173",
                "http://localhost:3000",
                "http://127.0.0.1:5173",
                "http://127.0.0.1:3000"
        ));

        config.setAllowedMethods(List.of(
                "GET", "POST", "PUT", "DELETE", "OPTIONS", "HEAD", "PATCH"
        ));

        config.setAllowedHeaders(List.of(
                HttpHeaders.AUTHORIZATION,
                HttpHeaders.CONTENT_TYPE,
                HttpHeaders.ACCEPT,
                HttpHeaders.ORIGIN,
                "X-Requested-With",
                "X-User-Id"
        ));

        config.setExposedHeaders(List.of(
                HttpHeaders.AUTHORIZATION,
                "X-User-Id"
        ));

        config.setAllowCredentials(true);
        config.setMaxAge(3600L); // Cache preflight for 1 hour

        return config;
    }
}
