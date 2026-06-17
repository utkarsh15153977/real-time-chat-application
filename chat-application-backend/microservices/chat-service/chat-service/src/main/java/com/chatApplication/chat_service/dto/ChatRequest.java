package com.chatApplication.chat_service.dto;

import lombok.Data;

@Data
public class ChatRequest {
    private Long senderId;
    private Long receiverId;
}
