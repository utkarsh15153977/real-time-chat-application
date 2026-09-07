package com.chatbackend.chat_application_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TypingEventDTO {
    private Long chatRoomId;
    private Long userId;
    private boolean isTyping;
}
