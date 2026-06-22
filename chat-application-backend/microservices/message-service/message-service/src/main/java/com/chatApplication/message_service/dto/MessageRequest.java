package com.chatApplication.message_service.dto;

import lombok.Data;

@Data
public class MessageRequest {
    private String senderId;
    private String receiverId;
    private String message;
}
