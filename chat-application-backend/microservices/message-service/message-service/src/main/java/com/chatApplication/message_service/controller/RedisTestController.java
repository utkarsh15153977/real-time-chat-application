package com.chatApplication.message_service.controller;

import com.chatApplication.message_service.service.PresenceTracker;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/test")
public class RedisTestController {
    private final PresenceTracker presenceTracker;

    @GetMapping("/online/{userId}")
    public String online(
            @PathVariable String userId) {

        presenceTracker.userOnline(userId);

        return "ONLINE";
    }

    @GetMapping("/status/{userId}")
    public Boolean status(
            @PathVariable String userId) {

        return presenceTracker.isOnline(userId);
    }
}
