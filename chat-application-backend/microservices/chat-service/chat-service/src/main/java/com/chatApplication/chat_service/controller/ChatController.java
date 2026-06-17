package com.chatApplication.chat_service.controller;

import com.chatApplication.chat_service.dto.ChatRequest;
import com.chatApplication.chat_service.dto.ChatResponse;
import com.chatApplication.chat_service.dto.GroupRequest;
import com.chatApplication.chat_service.service.ChatService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/chats")
@CrossOrigin("*")
public class ChatController {
    private final ChatService chatService;
    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }
    //Create private Chat.
    @PostMapping("/private")
    public ResponseEntity<ChatResponse> createPrivateChat(@RequestBody ChatRequest chatRequest) {
        return ResponseEntity.ok(chatService.createPrivateChat(chatRequest));
    }

    //Create Group Chat.
    @PutMapping("/group")
    public ResponseEntity<ChatResponse> createGroupChat(@RequestBody GroupRequest groupRequest) {
        return ResponseEntity.ok(chatService.createGroupChat(groupRequest));
    }

    //Get all chats of a user.
    @GetMapping("{userId}")
    public ResponseEntity<ChatResponse> getGroupChat(@PathVariable("userId") Long userId) {
        return ResponseEntity.ok((ChatResponse) chatService.getAllChats(userId));
    }

    @PutMapping("{userId}/add")
    public ResponseEntity<ChatResponse> addMemberToGroup(@PathVariable("userId") Long userId,
                                                         @RequestParam Long groupId) {
        ChatResponse response = chatService.addGroupMember(groupId, userId);
        //return ResponseEntity.ok("Member added Successfully");
        return ResponseEntity.ok(
                response
        );
    }
}
