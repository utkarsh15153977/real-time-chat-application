package com.chatApplication.message_service.controller;

import com.chatApplication.message_service.dto.GroupMessageRequest;
import com.chatApplication.message_service.service.GroupService;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;

@Controller
public class GroupChatWebSocketController {
    private final GroupService groupService;
    public GroupChatWebSocketController(GroupService groupService) {
        this.groupService = groupService;
    }

    @MessageMapping("/group.send")
    public void sendGroupMessage(
            GroupMessageRequest request) {
        groupService.sendMessage(
                request);
    }
}
