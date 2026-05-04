package com.chatbackend.chat_application_backend.controller;

import com.chatbackend.chat_application_backend.dto.MessageRequest;
import com.chatbackend.chat_application_backend.dto.MessageResponse;
import com.chatbackend.chat_application_backend.entity.User;
import com.chatbackend.chat_application_backend.mapper.MessageMapper;
import com.chatbackend.chat_application_backend.service.MessageService;
import com.chatbackend.chat_application_backend.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
@RequiredArgsConstructor
public class WebSocketController {
    private final MessageService messageService;
    private final SimpMessagingTemplate messagingTemplate;
    private final UserService userService;

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

        messagingTemplate.convertAndSend(
                "/topic/typing/" + request.getChatRoomId(),
                principal.getName() + " is typing..."
        );
    }
}
