package com.chatbackend.chat_application_backend.controller;

import com.chatbackend.chat_application_backend.dto.MessageRequest;
import com.chatbackend.chat_application_backend.dto.MessageResponse;
import com.chatbackend.chat_application_backend.mapper.MessageMapper;
import com.chatbackend.chat_application_backend.service.MessageService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/messages")
public class MessageController {
    private final MessageService messageService;

    public MessageController(MessageService messageService) {
        this.messageService = messageService;
    }

    //Sending messages API
    @PostMapping
    public ResponseEntity<MessageResponse> sendMessage(@Valid @RequestBody MessageRequest req) {
        return ResponseEntity.ok(
                MessageMapper.toMessageResponseDTO(
                        messageService.sendMessage(req.getSenderId(), req.getContent(), req.getChatRoomId())
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
}
