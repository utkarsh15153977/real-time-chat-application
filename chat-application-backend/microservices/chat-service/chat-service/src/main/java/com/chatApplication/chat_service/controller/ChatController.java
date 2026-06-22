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
//    @PostMapping("/group")
//    public ResponseEntity<ChatResponse> createGroupChat(@RequestBody GroupRequest groupRequest) {
//        return ResponseEntity.ok(chatService.createGroupChat(groupRequest));
//    }

    @PostMapping("/group")
    public ResponseEntity<ChatResponse> createGroupChat(
            @Valid @RequestBody GroupRequest groupRequest) {

        return ResponseEntity.ok(
                chatService.createGroupChat(groupRequest)
        );
    }

    //Get all chats of a user.
//    @GetMapping("{userId}")
//    public ResponseEntity<ChatResponse> getGroupChat(@PathVariable("userId") Long userId) {
//        return ResponseEntity.ok((ChatResponse) chatService.getAllChats(userId));
//    }

    @GetMapping("/{userId}")
    public ResponseEntity<List<ChatResponse>>
    getAllChats(
            @PathVariable Long userId){

        return ResponseEntity.ok(
                chatService.getAllChats(userId)
        );
    }
    @PutMapping("/groups/{groupId}/members")
    public ResponseEntity<ChatResponse> addMemberToGroup(@PathVariable("userId") Long userId,
                                                         @RequestParam Long groupId) {
        ChatResponse response = chatService.addGroupMember(groupId, userId);
        //return ResponseEntity.ok("Member added Successfully");
        return ResponseEntity.ok(
                response
        );
    }
    //Rename Group
    @PutMapping("/{chatId}/rename")
    public ResponseEntity<ChatResponse> renameGroup(@PathVariable long chatId,
                                                    @RequestBody RenameGroupChat renameGroupChat) {
        ChatResponse response = chatService.renameGroup(chatId, renameGroupChat.getName());
        return ResponseEntity.ok(response);
    }
    //Leaving member from group
    @DeleteMapping("/{chatId}/remove")
    public ResponseEntity<String> removeGroupMember(@PathVariable Long chatId,
                                                    @RequestParam Long userId) {
        chatService.removeGroupMember(chatId, userId);
        return ResponseEntity.ok("Member removed successfully");
    }
    //Get all members i  the group
//    @GetMapping("{chatId}/getMembers")
//    public ResponseEntity<ChatResponse> getGroupMembers(@PathVariable Long chatId) {
//        ChatResponse response = (ChatResponse) chatService.getGroupMembers(chatId);
//        return ResponseEntity.ok(response);
//    }

    @GetMapping("/{chatId}/members")
    public ResponseEntity<List<Long>>
    getGroupMembers(
            @PathVariable Long chatId){

        return ResponseEntity.ok(
                chatService.getGroupMembers(chatId)
        );
    }
    @DeleteMapping("/{chatId}/delete")
    public ResponseEntity<String> deleteGroup(@PathVariable Long chatId) {
        chatService.deleteGroup(chatId);
        return ResponseEntity.ok("Group deleted successfully");
    }

    @DeleteMapping("/{chatId}/leave")
    public ResponseEntity<String>
    leaveGroup(
            @PathVariable Long chatId,
            @RequestParam Long userId){

        chatService.leaveGroup(
                chatId,
                userId);

        return ResponseEntity.ok(
                "Left group successfully");
    }
}
