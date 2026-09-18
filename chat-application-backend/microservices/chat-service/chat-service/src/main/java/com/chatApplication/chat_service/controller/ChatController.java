package com.chatApplication.chat_service.controller;

import com.chatApplication.chat_service.dto.ChatRequest;
import com.chatApplication.chat_service.dto.ChatResponse;
import com.chatApplication.chat_service.dto.GroupRequest;
import com.chatApplication.chat_service.dto.RenameGroupChat;
import com.chatApplication.chat_service.exception.AuthorizationException;
import com.chatApplication.chat_service.service.ChatService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/chats")
public class ChatController {
    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    private Long getCallerId(HttpServletRequest request) {
        String userId = request.getHeader("X-User-Id");
        if (userId == null || userId.isBlank()) {
            throw new AuthorizationException("Unable to determine authenticated user identity");
        }
        try {
            return Long.parseLong(userId);
        } catch (NumberFormatException e) {
            throw new AuthorizationException("Invalid user identity in request");
        }
    }

    @PostMapping("/private")
    public ResponseEntity<ChatResponse> createPrivateChat(
            @Valid @RequestBody ChatRequest chatRequest) {
        return ResponseEntity.ok(chatService.createPrivateChat(chatRequest));
    }

    @PostMapping("/group")
    public ResponseEntity<ChatResponse> createGroupChat(
            @Valid @RequestBody GroupRequest groupRequest,
            HttpServletRequest request) {
        Long callerId = getCallerId(request);
        return ResponseEntity.ok(chatService.createGroupChat(groupRequest, callerId));
    }

    @GetMapping("/{userId}")
    public ResponseEntity<List<ChatResponse>> getAllChats(
            @PathVariable Long userId,
            HttpServletRequest request) {
        Long callerId = getCallerId(request);
        if (!callerId.equals(userId)) {
            throw new AuthorizationException("Cannot view another user's chats");
        }
        return ResponseEntity.ok(chatService.getAllChats(callerId));
    }

    @PutMapping("/groups/{groupId}/members")
    public ResponseEntity<ChatResponse> addMemberToGroup(
            @PathVariable Long groupId,
            @RequestParam Long userId,
            HttpServletRequest request) {
        Long callerId = getCallerId(request);
        return ResponseEntity.ok(chatService.addGroupMember(groupId, userId, callerId));
    }

    @PutMapping("/{chatId}/rename")
    public ResponseEntity<ChatResponse> renameGroup(
            @PathVariable Long chatId,
            @Valid @RequestBody RenameGroupChat renameGroupChat,
            HttpServletRequest request) {
        Long callerId = getCallerId(request);
        return ResponseEntity.ok(chatService.renameGroup(chatId, renameGroupChat.getName(), callerId));
    }

    @DeleteMapping("/{chatId}/remove")
    public ResponseEntity<String> removeGroupMember(
            @PathVariable Long chatId,
            @RequestParam Long userId,
            HttpServletRequest request) {
        Long callerId = getCallerId(request);
        chatService.removeGroupMember(chatId, userId, callerId);
        return ResponseEntity.ok("Member removed successfully");
    }

    @GetMapping("/{chatId}/members")
    public ResponseEntity<List<Long>> getGroupMembers(
            @PathVariable Long chatId,
            HttpServletRequest request) {
        Long callerId = getCallerId(request);
        return ResponseEntity.ok(chatService.getGroupMembers(chatId, callerId));
    }

    @DeleteMapping("/{chatId}/delete")
    public ResponseEntity<String> deleteGroup(
            @PathVariable Long chatId,
            HttpServletRequest request) {
        Long callerId = getCallerId(request);
        chatService.deleteGroup(chatId, callerId);
        return ResponseEntity.ok("Group deleted successfully");
    }

    @DeleteMapping("/{chatId}/leave")
    public ResponseEntity<String> leaveGroup(
            @PathVariable Long chatId,
            HttpServletRequest request) {
        Long callerId = getCallerId(request);
        chatService.leaveGroup(chatId, callerId);
        return ResponseEntity.ok("Left group successfully");
    }
}
