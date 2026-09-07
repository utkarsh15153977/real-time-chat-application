package com.chatbackend.chat_application_backend.service.serviceImpl;

import com.chatbackend.chat_application_backend.dto.PresenceEventDTO;
import com.chatbackend.chat_application_backend.dto.TypingEventDTO;
import com.chatbackend.chat_application_backend.service.PresenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class RedisPresenceServiceImpl implements PresenceService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final SimpMessagingTemplate messagingTemplate;

    private static final String PRESENCE_KEY_PREFIX = "presence:user:";
    private static final String TYPING_KEY_PREFIX = "typing:";
    private static final long PRESENCE_TTL_SECONDS = 60;
    private static final long TYPING_TTL_SECONDS = 3;

    @Override
    public void setUserOnline(Long userId) {
        String key = PRESENCE_KEY_PREFIX + userId;
        redisTemplate.opsForValue().set(key, "ONLINE", PRESENCE_TTL_SECONDS, TimeUnit.SECONDS);
        log.info("User {} marked ONLINE in Redis", userId);
        messagingTemplate.convertAndSend("/topic/presence",
                new PresenceEventDTO(userId, "ONLINE"));
    }

    @Override
    public void setUserOffline(Long userId) {
        String key = PRESENCE_KEY_PREFIX + userId;
        redisTemplate.delete(key);
        log.info("User {} marked OFFLINE in Redis", userId);
        messagingTemplate.convertAndSend("/topic/presence",
                new PresenceEventDTO(userId, "OFFLINE"));
    }

    @Override
    public void heartbeat(Long userId) {
        String key = PRESENCE_KEY_PREFIX + userId;
        Boolean exists = redisTemplate.hasKey(key);
        if (Boolean.TRUE.equals(exists)) {
            redisTemplate.expire(key, PRESENCE_TTL_SECONDS, TimeUnit.SECONDS);
            log.debug("Heartbeat refreshed for user {}", userId);
        }
    }

    @Override
    public void setTyping(Long chatRoomId, Long userId, boolean isTyping) {
        String key = TYPING_KEY_PREFIX + chatRoomId + ":" + userId;
        if (isTyping) {
            redisTemplate.opsForValue().set(key, "TYPING", TYPING_TTL_SECONDS, TimeUnit.SECONDS);
        } else {
            redisTemplate.delete(key);
        }
        TypingEventDTO event = new TypingEventDTO(chatRoomId, userId, isTyping);
        messagingTemplate.convertAndSend("/topic/chat/" + chatRoomId + "/typing", event);
    }

    @Override
    public boolean isUserOnline(Long userId) {
        String key = PRESENCE_KEY_PREFIX + userId;
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }
}
