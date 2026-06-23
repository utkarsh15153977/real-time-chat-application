package com.chatApplication.message_service.controller;

import com.chatApplication.message_service.dto.ChatMessage;
import com.chatApplication.message_service.dto.TypingEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class ChatWebSocketController {
    private final SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/chat.send")
    public void sendMessage(
            ChatMessage message) {

        messagingTemplate.convertAndSendToUser(
                message.getReceiverId(),
                "/queue/messages",
                message);
    }

    @MessageMapping("/chat.typing")
    public void typing(
            TypingEvent event) {

        messagingTemplate.convertAndSendToUser(
                event.getReceiverId(),
                "/queue/typing",
                event);
    }
}
