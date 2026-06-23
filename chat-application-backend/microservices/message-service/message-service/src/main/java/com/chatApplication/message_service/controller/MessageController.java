package com.chatApplication.message_service.controller;

import com.chatApplication.message_service.dto.AttachmentRequest;
import com.chatApplication.message_service.dto.MessageRequest;
import com.chatApplication.message_service.dto.MessageResponse;
import com.chatApplication.message_service.service.MessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/messages")
@RequiredArgsConstructor
@CrossOrigin("*")
public class MessageController {
    private final MessageService messageService;

    @PostMapping
    public ResponseEntity<MessageResponse> sendMessage(
            @RequestBody MessageRequest request) {

        return ResponseEntity.ok(
                messageService.sendMessage(request));
    }

    @GetMapping("/{messageId}")
    public ResponseEntity<MessageResponse> getMessage(
            @PathVariable Long messageId) {

        return ResponseEntity.ok(
                messageService.getMessage(messageId));
    }

    @GetMapping("/conversation")
    public ResponseEntity<List<MessageResponse>> getConversation(
            @RequestParam String user1,
            @RequestParam String user2) {

        return ResponseEntity.ok(
                messageService.getConversation(
                        user1,
                        user2));
    }

    @PutMapping("/{messageId}/delivered")
    public ResponseEntity<MessageResponse> markAsDelivered(
            @PathVariable Long messageId) {

        return ResponseEntity.ok(
                messageService.markAsDelivered(messageId));
    }

    @PutMapping("/{messageId}/seen")
    public ResponseEntity<MessageResponse> markAsSeen(
            @PathVariable Long messageId) {

        return ResponseEntity.ok(
                messageService.markAsSeen(messageId));
    }

    @PutMapping("/seen-all")
    public ResponseEntity<String> markAllAsSeen(
            @RequestParam String senderId,
            @RequestParam String receiverId) {

        messageService.markAllAsSeen(
                senderId,
                receiverId);

        return ResponseEntity.ok(
                "Messages marked as seen");
    }

    @PutMapping("/{messageId}/edit")
    public ResponseEntity<MessageResponse> editMessage(
            @PathVariable Long messageId,
            @RequestParam String content) {

        return ResponseEntity.ok(
                messageService.editMessage(
                        messageId,
                        content));
    }

    @DeleteMapping("/{messageId}")
    public ResponseEntity<String> deleteMessage(
            @PathVariable Long messageId) {

        messageService.deleteMessage(messageId);

        return ResponseEntity.ok(
                "Message deleted successfully");
    }

    @GetMapping("/unread-count")
    public ResponseEntity<Long> getUnreadCount(
            @RequestParam String senderId,
            @RequestParam String receiverId) {

        return ResponseEntity.ok(
                messageService.getUnreadCount(
                        senderId,
                        receiverId));
    }

    @GetMapping("/recent/{userId}")
    public ResponseEntity<List<MessageResponse>> getRecentMessages(
            @PathVariable String userId) {

        return ResponseEntity.ok(
                messageService.getRecentMessages(
                        userId));
    }

    @GetMapping("/exists/{messageId}")
    public ResponseEntity<Boolean> exists(
            @PathVariable Long messageId) {

        return ResponseEntity.ok(
                messageService.exists(messageId));
    }

    @PostMapping(
            value = "/attachment",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<MessageResponse> sendAttachment(
            @RequestParam String senderId,
            @RequestParam String receiverId,
            @RequestPart MultipartFile file) {

        AttachmentRequest request =
                AttachmentRequest.builder()
                        .senderId(senderId)
                        .receiverId(receiverId)
                        .file(file)
                        .build();

        return ResponseEntity.ok(
                messageService.sendAttachment(request));
    }
}
