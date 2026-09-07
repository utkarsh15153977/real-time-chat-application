package com.chatApplication.message_service.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PresenceTrackerTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private SetOperations<String, Object> setOperations;

    @InjectMocks
    private PresenceTracker presenceTracker;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
    }

    @Test
    @DisplayName("should add user to online set")
    void userOnline_addsUserToSet() {
        presenceTracker.userOnline("user1");

        verify(setOperations).add("ONLINE_USERS", "user1");
    }

    @Test
    @DisplayName("should remove user from online set")
    void userOffline_removesUserFromSet() {
        presenceTracker.userOffline("user1");

        verify(setOperations).remove("ONLINE_USERS", "user1");
    }

    @Test
    @DisplayName("should return true when user is online")
    void isOnline_userInSet_returnsTrue() {
        when(setOperations.isMember("ONLINE_USERS", "user1"))
                .thenReturn(true);

        boolean result = presenceTracker.isOnline("user1");

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("should return false when user is offline")
    void isOnline_userNotInSet_returnsFalse() {
        when(setOperations.isMember("ONLINE_USERS", "user1"))
                .thenReturn(false);

        boolean result = presenceTracker.isOnline("user1");

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("should return false when isMember returns null")
    void isOnline_nullResult_returnsFalse() {
        when(setOperations.isMember("ONLINE_USERS", "user1"))
                .thenReturn(null);

        boolean result = presenceTracker.isOnline("user1");

        assertThat(result).isFalse();
    }
}
