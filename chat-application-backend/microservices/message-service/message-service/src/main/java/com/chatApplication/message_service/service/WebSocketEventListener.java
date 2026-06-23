package com.chatApplication.message_service.service;

import com.chatApplication.message_service.entity.UserPresence;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import com.chatApplication.message_service.repository.UserPresenceRepository;
import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class WebSocketEventListener {
    private final PresenceTracker
            presenceTracker;
    private final UserPresenceRepository userPresenceRepository;

    @EventListener
    public void handleConnect(
            SessionConnectEvent event) {

        StompHeaderAccessor accessor =
                StompHeaderAccessor.wrap(
                        event.getMessage());

        String userId =
                accessor.getFirstNativeHeader(
                        "userId");

        if(userId != null){

            presenceTracker.userOnline(
                    userId);
        }
    }

    @EventListener
    public void handleDisconnect(
            SessionDisconnectEvent event) {

        StompHeaderAccessor accessor =
                StompHeaderAccessor.wrap(
                        event.getMessage());

        String userId =
                accessor.getFirstNativeHeader(
                        "userId");

        if (userId == null) {
            return;
        }

        presenceTracker.userOffline(userId);

        UserPresence presence =
                userPresenceRepository
                        .findById(userId)
                        .orElse(new UserPresence());

        presence.setUserId(userId);
        presence.setOnline(false);
        presence.setLastSeen(LocalDateTime.now());

        userPresenceRepository.save(presence);
    }

}
