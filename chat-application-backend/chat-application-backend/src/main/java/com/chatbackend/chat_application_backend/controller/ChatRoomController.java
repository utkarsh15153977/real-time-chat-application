package com.chatbackend.chat_application_backend.controller;

import com.chatbackend.chat_application_backend.entity.ChatRoom;
import com.chatbackend.chat_application_backend.service.ChatService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/chatrooms")
public class ChatRoomController {
    private final ChatService chatService;

    public ChatRoomController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping("/private")
    public ResponseEntity<ChatRoom> createPrivateChat(@RequestBody Map<String, Long> request) {
        return ResponseEntity.ok(
                chatService.createPrivateChat(request.get("user1Id"), request.get("user2Id"))
        );
    }

    @PostMapping("/group")
    public ResponseEntity<ChatRoom> createGroupChat(@RequestParam String groupName,
                                                    @RequestBody List<Long> participantIds) {
        return ResponseEntity.ok(chatService.createGroupChat(groupName, participantIds));
    }

    @GetMapping("/{chatRoomId}")
    public ResponseEntity<ChatRoom> getChatRoom(@PathVariable Long chatRoomId) {
        return ResponseEntity.ok(chatService.getChatRoom(chatRoomId));
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<ChatRoom>> getUserChats(@PathVariable Long userId) {
        return ResponseEntity.ok(chatService.getUserChats(userId));
    }

    @PostMapping("/{chatRoomId}/participants/{userId}")
    public ResponseEntity<Void> addParticipant(@PathVariable Long chatRoomId,
                                               @PathVariable Long userId) {
        chatService.addParticipant(chatRoomId, userId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{chatRoomId}/participants/{userId}")
    public ResponseEntity<Void> removeParticipant(@PathVariable Long chatRoomId,
                                                  @PathVariable Long userId) {
        chatService.removeParticipant(chatRoomId, userId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{chatRoomId}")
    public ResponseEntity<Void> deleteChatRoom(@PathVariable Long chatRoomId) {
        chatService.deleteChatRoom(chatRoomId);
        return ResponseEntity.noContent().build();
    }
}
