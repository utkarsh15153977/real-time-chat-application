package com.chatbackend.chat_application_backend.websocket;

import com.chatbackend.chat_application_backend.entity.User;
import com.chatbackend.chat_application_backend.repository.UserRepository;
import com.chatbackend.chat_application_backend.service.PresenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketEventListener {

    private final PresenceService presenceService;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;

    private final Map<String, Long> sessionUserMap = new ConcurrentHashMap<>();

    @EventListener
    public void handleSessionConnected(SessionConnectEvent event) {
        Principal principal = event.getUser();
        if (principal == null) {
            return;
        }

        String username = principal.getName();
        User user = userRepository.findByEmail(username)
                .or(() -> userRepository.findByPhone(username))
                .orElse(null);

        if (user != null) {
            String sessionId = event.getMessage().getHeaders().get("simpSessionId", String.class);
            if (sessionId != null) {
                sessionUserMap.put(sessionId, user.getId());
            }
            presenceService.setUserOnline(user.getId());
            log.info("WebSocket connected: user {} (id={})", username, user.getId());
        }
    }

    @EventListener
    public void handleSessionDisconnect(SessionDisconnectEvent event) {
        String sessionId = event.getSessionId();
        Long userId = sessionUserMap.remove(sessionId);

        if (userId != null) {
            presenceService.setUserOffline(userId);
            log.info("WebSocket disconnected: userId={}", userId);
        }
    }
}
