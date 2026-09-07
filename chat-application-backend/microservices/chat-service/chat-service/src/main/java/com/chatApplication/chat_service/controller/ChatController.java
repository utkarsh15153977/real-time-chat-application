package com.chatApplication.chat_service.controller;

import com.chatApplication.chat_service.dto.ChatRequest;
import com.chatApplication.chat_service.dto.ChatResponse;
import com.chatApplication.chat_service.dto.GroupRequest;
import com.chatApplication.chat_service.dto.RenameGroupChat;
import com.chatApplication.chat_service.service.ChatService;
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

    @PostMapping("/private")
    public ResponseEntity<ChatResponse> createPrivateChat(
            @Valid @RequestBody ChatRequest chatRequest) {
        return ResponseEntity.ok(chatService.createPrivateChat(chatRequest));
    }

    @PostMapping("/group")
    public ResponseEntity<ChatResponse> createGroupChat(
            @Valid @RequestBody GroupRequest groupRequest) {
        return ResponseEntity.ok(chatService.createGroupChat(groupRequest));
    }

    @GetMapping("/{userId}")
    public ResponseEntity<List<ChatResponse>> getAllChats(
            @PathVariable Long userId) {
        return ResponseEntity.ok(chatService.getAllChats(userId));
    }

    @PutMapping("/groups/{groupId}/members")
    public ResponseEntity<ChatResponse> addMemberToGroup(
            @PathVariable Long groupId,
            @RequestParam Long userId) {
        return ResponseEntity.ok(chatService.addGroupMember(groupId, userId));
    }

    @PutMapping("/{chatId}/rename")
    public ResponseEntity<ChatResponse> renameGroup(
            @PathVariable Long chatId,
            @Valid @RequestBody RenameGroupChat renameGroupChat) {
        return ResponseEntity.ok(chatService.renameGroup(chatId, renameGroupChat.getName()));
    }

    @DeleteMapping("/{chatId}/remove")
    public ResponseEntity<String> removeGroupMember(
            @PathVariable Long chatId,
            @RequestParam Long userId) {
        chatService.removeGroupMember(chatId, userId);
        return ResponseEntity.ok("Member removed successfully");
    }

    @GetMapping("/{chatId}/members")
    public ResponseEntity<List<Long>> getGroupMembers(
            @PathVariable Long chatId) {
        return ResponseEntity.ok(chatService.getGroupMembers(chatId));
    }

    @DeleteMapping("/{chatId}/delete")
    public ResponseEntity<String> deleteGroup(@PathVariable Long chatId) {
        chatService.deleteGroup(chatId);
        return ResponseEntity.ok("Group deleted successfully");
    }

    @DeleteMapping("/{chatId}/leave")
    public ResponseEntity<String> leaveGroup(
            @PathVariable Long chatId,
            @RequestParam Long userId) {
        chatService.leaveGroup(chatId, userId);
        return ResponseEntity.ok("Left group successfully");
    }
}
