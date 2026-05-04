package com.chatbackend.chat_application_backend.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class StatusMessageResponse {
    private Long senderId;
    private String imageUrl;
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;
    private String statusMessage;
}
