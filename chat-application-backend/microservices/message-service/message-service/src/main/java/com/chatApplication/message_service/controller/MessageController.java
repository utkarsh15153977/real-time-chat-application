package com.chatApplication.message_service.controller;

import com.chatApplication.message_service.dto.AttachmentRequest;
import com.chatApplication.message_service.dto.MessageRequest;
import com.chatApplication.message_service.dto.MessageResponse;
import com.chatApplication.message_service.entity.Message;
import com.chatApplication.message_service.exception.ForbiddenException;
import com.chatApplication.message_service.repository.MessageRepository;
import com.chatApplication.message_service.service.MessageService;
import jakarta.servlet.http.HttpServletRequest;
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
    private final MessageRepository messageRepository;

    private String getAuthenticatedUserId(HttpServletRequest httpRequest) {
        String userId = httpRequest.getHeader("X-User-Id");
        if (userId == null || userId.isBlank()) {
            return null;
        }
        return userId;
    }

    private Message getMessageOrThrow(Long messageId) {
        return messageRepository.findById(messageId)
                .orElseThrow(() -> new RuntimeException("Message not found"));
    }

    private void verifyParticipant(Message message, String authenticatedUserId) {
        if (!message.getSenderId().equals(authenticatedUserId)
                && !message.getReceiverId().equals(authenticatedUserId)) {
            throw new ForbiddenException("You are not authorized to access this message");
        }
    }

    private void verifySender(Message message, String authenticatedUserId) {
        if (!message.getSenderId().equals(authenticatedUserId)) {
            throw new ForbiddenException("Only the sender can perform this action");
        }
    }

    private void verifyReceiver(Message message, String authenticatedUserId) {
        if (!message.getReceiverId().equals(authenticatedUserId)) {
            throw new ForbiddenException("Only the recipient can perform this action");
        }
    }

    @PostMapping
    public ResponseEntity<MessageResponse> sendMessage(
            @RequestBody MessageRequest request,
            HttpServletRequest httpRequest) {

        String authenticatedUserId = getAuthenticatedUserId(httpRequest);
        if (authenticatedUserId == null) {
            return ResponseEntity.status(401).build();
        }
        request.setSenderId(authenticatedUserId);

        return ResponseEntity.ok(
                messageService.sendMessage(request));
    }

    @GetMapping("/{messageId}")
    public ResponseEntity<?> getMessage(
            @PathVariable Long messageId,
            HttpServletRequest httpRequest) {

        String authenticatedUserId = getAuthenticatedUserId(httpRequest);
        if (authenticatedUserId == null) {
            return ResponseEntity.status(401).build();
        }

        Message message = getMessageOrThrow(messageId);
        verifyParticipant(message, authenticatedUserId);

        return ResponseEntity.ok(
                messageService.getMessage(messageId));
    }

    @GetMapping("/conversation")
    public ResponseEntity<?> getConversation(
            @RequestParam String user1,
            @RequestParam String user2,
            HttpServletRequest httpRequest) {

        String authenticatedUserId = getAuthenticatedUserId(httpRequest);
        if (authenticatedUserId == null) {
            return ResponseEntity.status(401).build();
        }

        if (!authenticatedUserId.equals(user1) && !authenticatedUserId.equals(user2)) {
            return ResponseEntity.status(403).build();
        }

        return ResponseEntity.ok(
                messageService.getConversation(user1, user2));
    }

    @PutMapping("/{messageId}/delivered")
    public ResponseEntity<?> markAsDelivered(
            @PathVariable Long messageId,
            HttpServletRequest httpRequest) {

        String authenticatedUserId = getAuthenticatedUserId(httpRequest);
        if (authenticatedUserId == null) {
            return ResponseEntity.status(401).build();
        }

        Message message = getMessageOrThrow(messageId);
        verifyReceiver(message, authenticatedUserId);

        return ResponseEntity.ok(
                messageService.markAsDelivered(messageId));
    }

    @PutMapping("/{messageId}/seen")
    public ResponseEntity<?> markAsSeen(
            @PathVariable Long messageId,
            HttpServletRequest httpRequest) {

        String authenticatedUserId = getAuthenticatedUserId(httpRequest);
        if (authenticatedUserId == null) {
            return ResponseEntity.status(401).build();
        }

        Message message = getMessageOrThrow(messageId);
        verifyReceiver(message, authenticatedUserId);

        return ResponseEntity.ok(
                messageService.markAsSeen(messageId));
    }

    @PutMapping("/seen-all")
    public ResponseEntity<?> markAllAsSeen(
            @RequestParam String senderId,
            @RequestParam String receiverId,
            HttpServletRequest httpRequest) {

        String authenticatedUserId = getAuthenticatedUserId(httpRequest);
        if (authenticatedUserId == null) {
            return ResponseEntity.status(401).build();
        }

        if (!authenticatedUserId.equals(receiverId)) {
            return ResponseEntity.status(403).build();
        }

        messageService.markAllAsSeen(senderId, receiverId);

        return ResponseEntity.ok("Messages marked as seen");
    }

    @PutMapping("/{messageId}/edit")
    public ResponseEntity<?> editMessage(
            @PathVariable Long messageId,
            @RequestParam String content,
            HttpServletRequest httpRequest) {

        String authenticatedUserId = getAuthenticatedUserId(httpRequest);
        if (authenticatedUserId == null) {
            return ResponseEntity.status(401).build();
        }

        Message message = getMessageOrThrow(messageId);
        verifySender(message, authenticatedUserId);

        return ResponseEntity.ok(
                messageService.editMessage(messageId, content));
    }

    @DeleteMapping("/{messageId}")
    public ResponseEntity<?> deleteMessage(
            @PathVariable Long messageId,
            HttpServletRequest httpRequest) {

        String authenticatedUserId = getAuthenticatedUserId(httpRequest);
        if (authenticatedUserId == null) {
            return ResponseEntity.status(401).build();
        }

        Message message = getMessageOrThrow(messageId);
        verifySender(message, authenticatedUserId);

        messageService.deleteMessage(messageId);

        return ResponseEntity.ok("Message deleted successfully");
    }

    @GetMapping("/unread-count")
    public ResponseEntity<?> getUnreadCount(
            @RequestParam String senderId,
            @RequestParam String receiverId,
            HttpServletRequest httpRequest) {

        String authenticatedUserId = getAuthenticatedUserId(httpRequest);
        if (authenticatedUserId == null) {
            return ResponseEntity.status(401).build();
        }

        if (!authenticatedUserId.equals(receiverId)) {
            return ResponseEntity.status(403).build();
        }

        return ResponseEntity.ok(
                messageService.getUnreadCount(senderId, receiverId));
    }

    @GetMapping("/recent/{userId}")
    public ResponseEntity<?> getRecentMessages(
            @PathVariable String userId,
            HttpServletRequest httpRequest) {

        String authenticatedUserId = getAuthenticatedUserId(httpRequest);
        if (authenticatedUserId == null) {
            return ResponseEntity.status(401).build();
        }

        if (!authenticatedUserId.equals(userId)) {
            return ResponseEntity.status(403).build();
        }

        return ResponseEntity.ok(
                messageService.getRecentMessages(userId));
    }

    @GetMapping("/exists/{messageId}")
    public ResponseEntity<?> exists(
            @PathVariable Long messageId,
            HttpServletRequest httpRequest) {

        String authenticatedUserId = getAuthenticatedUserId(httpRequest);
        if (authenticatedUserId == null) {
            return ResponseEntity.status(401).build();
        }

        Message message = messageRepository.findById(messageId).orElse(null);
        if (message == null) {
            return ResponseEntity.ok(false);
        }

        if (!message.getSenderId().equals(authenticatedUserId)
                && !message.getReceiverId().equals(authenticatedUserId)) {
            return ResponseEntity.status(403).build();
        }

        return ResponseEntity.ok(messageService.exists(messageId));
    }

    @PostMapping(
            value = "/attachment",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<MessageResponse> sendAttachment(
            @RequestParam String receiverId,
            @RequestPart MultipartFile file,
            HttpServletRequest httpRequest) {

        String authenticatedUserId = getAuthenticatedUserId(httpRequest);
        if (authenticatedUserId == null) {
            return ResponseEntity.status(401).build();
        }

        AttachmentRequest request =
                AttachmentRequest.builder()
                        .senderId(authenticatedUserId)
                        .receiverId(receiverId)
                        .file(file)
                        .build();

        return ResponseEntity.ok(
                messageService.sendAttachment(request));
    }
}
