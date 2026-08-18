package com.chatApplication.message_service.dto;

import com.chatApplication.message_service.entity.MessageStatus;
import lombok.Data;

@Data
public class MessageRequest {
    private String senderId;
    private String receiverId;
    private String message;
    private MessageStatus status;
}
