package com.chatApplication.message_service.controller;

import com.chatApplication.message_service.entity.Group;
import com.chatApplication.message_service.entity.GroupMessage;
import com.chatApplication.message_service.service.GroupService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/groups")
public class GroupController {
    private final GroupService groupService;

    public GroupController(GroupService groupService) {
        this.groupService = groupService;
    }

    @PostMapping
    public Group createGroup(
            @RequestParam String name,
            @RequestParam String creator) {

        return groupService.createGroup(
                name,
                creator);
    }

    @PostMapping("/{groupId}/members")
    public void addMember(
            @PathVariable Long groupId,
            @RequestParam String userId) {

        groupService.addMember(
                groupId,
                userId);
    }

    @GetMapping("/{groupId}/messages")
    public List<GroupMessage> getMessages(
            @PathVariable Long groupId) {

        return groupService.getMessages(
                groupId);
    }
}
