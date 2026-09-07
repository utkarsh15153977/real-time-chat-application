package com.chatApplication.message_service.controller;

import com.chatApplication.message_service.dto.PresenceEvent;
import com.chatApplication.message_service.service.PresenceTracker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;

import java.security.Principal;

/**
 * STOMP WebSocket controller for user presence tracking.
 * <p>
 * Security: userId is extracted from the authenticated Principal,
 * never from the client-provided payload.
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class PresenceController {

    private final PresenceTracker presenceTracker;
    private final SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/presence.online")
    public void online(Principal principal) {
        String userId = extractUserId(principal);
        if (userId == null) {
            return;
        }

        presenceTracker.userOnline(userId);

        messagingTemplate.convertAndSend(
                "/topic/presence",
                PresenceEvent.builder()
                        .userId(userId)
                        .status("ONLINE")
                        .build());
    }

    @MessageMapping("/presence.offline")
    public void offline(Principal principal) {
        String userId = extractUserId(principal);
        if (userId == null) {
            return;
        }

        presenceTracker.userOffline(userId);

        messagingTemplate.convertAndSend(
                "/topic/presence",
                PresenceEvent.builder()
                        .userId(userId)
                        .status("OFFLINE")
                        .build());
    }

    private String extractUserId(Principal principal) {
        if (principal == null) {
            return null;
        }
        if (principal instanceof Authentication auth) {
            return auth.getName();
        }
        return principal.getName();
    }
}
