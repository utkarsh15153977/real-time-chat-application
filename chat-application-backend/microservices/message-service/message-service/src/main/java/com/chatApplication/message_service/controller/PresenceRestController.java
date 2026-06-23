package com.chatApplication.message_service.controller;

import com.chatApplication.message_service.service.PresenceTracker;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/presence")
@RequiredArgsConstructor
public class PresenceRestController {
    private final PresenceTracker
            presenceTracker;

    @GetMapping("/{userId}")
    public Boolean isOnline(
            @PathVariable String userId) {

        return presenceTracker
                .isOnline(userId);
    }
}
