package com.chatbackend.chat_application_backend.controller;

import com.chatbackend.chat_application_backend.dto.MessageRequest;
import com.chatbackend.chat_application_backend.dto.MessageResponse;
import com.chatbackend.chat_application_backend.entity.User;
import com.chatbackend.chat_application_backend.mapper.MessageMapper;
import com.chatbackend.chat_application_backend.service.MessageService;
import com.chatbackend.chat_application_backend.service.PresenceService;
import com.chatbackend.chat_application_backend.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.Map;

@Controller
@RequiredArgsConstructor
public class WebSocketController {
    private final MessageService messageService;
    private final SimpMessagingTemplate messagingTemplate;
    private final UserService userService;
    private final PresenceService presenceService;

    @MessageMapping("/chat.send")
    public void sendMessage(MessageRequest request, Principal principal) {
        if (principal == null) {
            throw new RuntimeException("Unauthorized");
        }

        User sender = userService.findByEmailOrPhone(principal.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));

        MessageResponse response = MessageMapper.toMessageResponseDTO(
                messageService.sendMessage(sender.getId(), request.getContent(), request.getChatRoomId())
        );

        messagingTemplate.convertAndSend("/topic/chat/" + request.getChatRoomId(), response);
    }

    @MessageMapping("/chat.typing")
    public void typing(MessageRequest request, Principal principal) {
        if (principal == null) {
            throw new RuntimeException("Unauthorized");
        }

        User sender = userService.findByEmailOrPhone(principal.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));

        presenceService.setTyping(request.getChatRoomId(), sender.getId(), true);
    }

    @MessageMapping("/chat.stopTyping")
    public void stopTyping(MessageRequest request, Principal principal) {
        if (principal == null) {
            throw new RuntimeException("Unauthorized");
        }

        User sender = userService.findByEmailOrPhone(principal.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));

        presenceService.setTyping(request.getChatRoomId(), sender.getId(), false);
    }

    @MessageMapping("/chat.delivered")
    public void markDelivered(@Payload Map<String, Long> payload, Principal principal) {
        if (principal == null) {
            throw new RuntimeException("Unauthorized");
        }

        User user = userService.findByEmailOrPhone(principal.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));

        Long messageId = payload.get("messageId");
        if (messageId != null) {
            messageService.markAsDelivered(messageId, user.getId());
        }
    }

    @MessageMapping("/chat.read")
    public void markRead(@Payload Map<String, Long> payload, Principal principal) {
        if (principal == null) {
            throw new RuntimeException("Unauthorized");
        }

        User user = userService.findByEmailOrPhone(principal.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));

        Long chatRoomId = payload.get("chatRoomId");
        if (chatRoomId != null) {
            messageService.markAsRead(chatRoomId, user.getId());
        }
    }

    @MessageMapping("/heartbeat")
    public void heartbeat(Principal principal) {
        if (principal == null) {
            return;
        }

        User user = userService.findByEmailOrPhone(principal.getName())
                .orElse(null);
        if (user != null) {
            presenceService.heartbeat(user.getId());
        }
    }
}
