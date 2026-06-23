package com.chatApplication.message_service.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.data.redis.core.RedisTemplate;

@Component
@RequiredArgsConstructor
public class PresenceTracker {

    private final RedisTemplate<String, Object> redisTemplate;

    private static final String ONLINE_USERS =
            "ONLINE_USERS";

    public void userOnline(
            String userId) {

        redisTemplate.opsForSet()
                .add(ONLINE_USERS, userId);
    }

    public void userOffline(
            String userId) {

        redisTemplate.opsForSet()
                .remove(ONLINE_USERS, userId);
    }

    public boolean isOnline(
            String userId) {

        return Boolean.TRUE.equals(
                redisTemplate.opsForSet()
                        .isMember(
                                ONLINE_USERS,
                                userId));
    }
}