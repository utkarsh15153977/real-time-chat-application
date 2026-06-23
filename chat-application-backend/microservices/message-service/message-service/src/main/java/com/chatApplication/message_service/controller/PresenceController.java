package com.chatApplication.message_service.controller;

import com.chatApplication.message_service.dto.PresenceEvent;
import com.chatApplication.message_service.service.PresenceTracker;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class PresenceController {
    private final PresenceTracker
            presenceTracker;

    private final SimpMessagingTemplate
            messagingTemplate;

    @MessageMapping("/presence.online")
    public void online(
            String userId) {

        presenceTracker.userOnline(
                userId);

        messagingTemplate.convertAndSend(
                "/topic/presence",
                PresenceEvent.builder()
                        .userId(userId)
                        .status("ONLINE")
                        .build());
    }

    @MessageMapping("/presence.offline")
    public void offline(
            String userId) {

        presenceTracker.userOffline(
                userId);

        messagingTemplate.convertAndSend(
                "/topic/presence",
                PresenceEvent.builder()
                        .userId(userId)
                        .status("OFFLINE")
                        .build());
    }
}
