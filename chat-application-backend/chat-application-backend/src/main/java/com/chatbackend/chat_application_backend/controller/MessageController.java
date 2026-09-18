package com.chatbackend.chat_application_backend.controller;

import com.chatbackend.chat_application_backend.dto.MessageRequest;
import com.chatbackend.chat_application_backend.dto.MessageResponse;
import com.chatbackend.chat_application_backend.entity.ChatRoom;
import com.chatbackend.chat_application_backend.entity.Message;
import com.chatbackend.chat_application_backend.entity.User;
import com.chatbackend.chat_application_backend.mapper.MessageMapper;
import com.chatbackend.chat_application_backend.repository.ChatRepository;
import com.chatbackend.chat_application_backend.repository.MessageRepository;
import com.chatbackend.chat_application_backend.repository.UserRepository;
import com.chatbackend.chat_application_backend.service.MessageService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
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
    private final MessageRepository messageRepository;
    private final ChatRepository chatRepository;

    public MessageController(MessageService messageService, UserRepository userRepository,
                             MessageRepository messageRepository, ChatRepository chatRepository) {
        this.messageService = messageService;
        this.userRepository = userRepository;
        this.messageRepository = messageRepository;
        this.chatRepository = chatRepository;
    }

    private User getAuthenticatedUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = auth.getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Authenticated user not found"));
    }

    private ChatRoom getChatRoomOrThrow(Long chatRoomId) {
        return chatRepository.findById(chatRoomId)
                .orElseThrow(() -> new RuntimeException("Chat room not found"));
    }

    private void verifyChatParticipant(ChatRoom chatRoom, Long userId) {
        boolean isParticipant = chatRoom.getParticipants().stream()
                .anyMatch(p -> p.getId().equals(userId));
        if (!isParticipant) {
            throw new AccessDeniedException("You are not a participant of this chat room");
        }
    }

    private void verifyMessageSender(Message message, Long userId) {
        if (!message.getSender().getId().equals(userId)) {
            throw new AccessDeniedException("Only the sender can perform this action");
        }
    }

    private Message getMessageOrThrow(Long messageId) {
        return messageRepository.findById(messageId)
                .orElseThrow(() -> new RuntimeException("Message not found"));
    }

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
        User authenticatedUser = getAuthenticatedUser();
        ChatRoom chatRoom = getChatRoomOrThrow(chatRoomId);
        verifyChatParticipant(chatRoom, authenticatedUser.getId());

        return ResponseEntity.ok(
                MessageMapper.toMessageResponseDTO(messageService.getMessagesByChatRoom(chatRoomId))
        );
    }

    @PutMapping("/{messageId}/read")
    public ResponseEntity<Void> markAsRead(@PathVariable Long messageId) {
        User authenticatedUser = getAuthenticatedUser();
        Message message = getMessageOrThrow(messageId);
        verifyChatParticipant(message.getChatRoom(), authenticatedUser.getId());

        messageService.markMessageRead(messageId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{messageId}")
    public ResponseEntity<Void> deleteMessage(@PathVariable Long messageId) {
        User authenticatedUser = getAuthenticatedUser();
        Message message = getMessageOrThrow(messageId);
        verifyMessageSender(message, authenticatedUser.getId());

        messageService.deleteMessage(messageId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{messageId}/delivered")
    public ResponseEntity<Void> markAsDelivered(@PathVariable Long messageId) {
        User authenticatedUser = getAuthenticatedUser();
        Message message = getMessageOrThrow(messageId);
        verifyChatParticipant(message.getChatRoom(), authenticatedUser.getId());

        messageService.markAsDelivered(messageId, authenticatedUser.getId());
        return ResponseEntity.ok().build();
    }

    @PutMapping("/chat/{chatRoomId}/read-all")
    public ResponseEntity<Void> markAllAsRead(@PathVariable Long chatRoomId) {
        User authenticatedUser = getAuthenticatedUser();
        ChatRoom chatRoom = getChatRoomOrThrow(chatRoomId);
        verifyChatParticipant(chatRoom, authenticatedUser.getId());

        messageService.markAsRead(chatRoomId, authenticatedUser.getId());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/chat/{chatRoomId}/unread-count")
    public ResponseEntity<Map<String, Long>> getUnreadCount(@PathVariable Long chatRoomId) {
        User authenticatedUser = getAuthenticatedUser();
        ChatRoom chatRoom = getChatRoomOrThrow(chatRoomId);
        verifyChatParticipant(chatRoom, authenticatedUser.getId());

        long count = messageService.getUnreadMessageCount(authenticatedUser.getId(), chatRoomId);
        return ResponseEntity.ok(Map.of("unreadCount", count));
    }
}
