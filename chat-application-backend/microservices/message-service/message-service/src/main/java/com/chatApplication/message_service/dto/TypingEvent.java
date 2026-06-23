package com.chatApplication.message_service.dto;

import lombok.Data;

@Data
public class TypingEvent {
    private String senderId;
    private String receiverId;
    private boolean typing;
}
