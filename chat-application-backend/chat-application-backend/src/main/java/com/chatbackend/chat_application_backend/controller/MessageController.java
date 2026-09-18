package com.chatbackend.chat_application_backend.controller;

import com.chatbackend.chat_application_backend.dto.MessageRequest;
import com.chatbackend.chat_application_backend.dto.MessageResponse;
import com.chatbackend.chat_application_backend.entity.User;
import com.chatbackend.chat_application_backend.mapper.MessageMapper;
import com.chatbackend.chat_application_backend.repository.UserRepository;
import com.chatbackend.chat_application_backend.service.MessageService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/messages")
public class MessageController {
    private final MessageService messageService;
    private final UserRepository userRepository;

    public MessageController(MessageService messageService, UserRepository userRepository) {
        this.messageService = messageService;
        this.userRepository = userRepository;
    }

    // Sending messages API
    @PostMapping
    public ResponseEntity<MessageResponse> sendMessage(@Valid @RequestBody MessageRequest req) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = auth.getName();
        User authenticatedUser = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Authenticated user not found"));

        return ResponseEntity.ok(
                MessageMapper.toMessageResponseDTO(
                        messageService.sendMessage(authenticatedUser.getId(), req.getContent(), req.getChatRoomId())
                )
        );
    }

    @GetMapping("/chat/{chatRoomId}")
    public ResponseEntity<List<MessageResponse>> getMessages(@PathVariable Long chatRoomId) {
        return ResponseEntity.ok(
                MessageMapper.toMessageResponseDTO(messageService.getMessagesByChatRoom(chatRoomId))
        );
    }

    @PutMapping("/{messageId}/read")
    public ResponseEntity<Void> markAsRead(@PathVariable Long messageId) {
        messageService.markMessageRead(messageId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{messageId}")
    public ResponseEntity<Void> deleteMessage(@PathVariable Long messageId) {
        messageService.deleteMessage(messageId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{messageId}/delivered")
    public ResponseEntity<Void> markAsDelivered(@PathVariable Long messageId,
                                                @RequestBody Map<String, Long> request) {
        Long userId = request.get("userId");
        messageService.markAsDelivered(messageId, userId);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/chat/{chatRoomId}/read-all")
    public ResponseEntity<Void> markAllAsRead(@PathVariable Long chatRoomId,
                                              @RequestParam Long userId) {
        messageService.markAsRead(chatRoomId, userId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/chat/{chatRoomId}/unread-count")
    public ResponseEntity<Map<String, Long>> getUnreadCount(@PathVariable Long chatRoomId,
                                                            @RequestParam Long userId) {
        long count = messageService.getUnreadMessageCount(userId, chatRoomId);
        return ResponseEntity.ok(Map.of("unreadCount", count));
    }
}
