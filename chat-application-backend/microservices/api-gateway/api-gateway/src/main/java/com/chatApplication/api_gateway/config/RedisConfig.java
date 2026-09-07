package com.chatApplication.api_gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;

/**
 * Redis configuration for the API Gateway.
 * <p>
 * Provides reactive Redis template for:
 *   - Token blacklist revocation (checking revoked JWTs)
 *   - Rate limiting (future use)
 *   - Session caching (future use)
 * <p>
 * Uses ReactiveStringRedisTemplate for non-blocking operations
 * that integrate with the WebFlux event loop.
 */
@Configuration
public class RedisConfig {

    /**
     * Reactive Redis template for string operations.
     * Used primarily for token blacklist lookups.
     *
     * @param connectionFactory the reactive Redis connection factory
     * @return ReactiveStringRedisTemplate instance
     */
    @Bean
    public ReactiveStringRedisTemplate reactiveRedisTemplate(
            ReactiveRedisConnectionFactory connectionFactory) {
        return new ReactiveStringRedisTemplate(connectionFactory);
    }
}
