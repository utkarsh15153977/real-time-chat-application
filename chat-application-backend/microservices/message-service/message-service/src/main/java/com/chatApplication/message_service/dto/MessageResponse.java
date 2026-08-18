package com.chatApplication.message_service.dto;

import com.chatApplication.message_service.entity.MessageStatus;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class MessageResponse {
    private String senderId;
    private String receiverId;
    private String message;
    private MessageStatus status;
    private LocalDateTime sendTime;
}