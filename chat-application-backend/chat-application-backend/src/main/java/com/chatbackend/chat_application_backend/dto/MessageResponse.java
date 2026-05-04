package com.chatbackend.chat_application_backend.dto;

import com.chatbackend.chat_application_backend.entity.MessageStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class MessageResponse {
    private Long id;
    private Long senderId;
    private Long chatRoomId;
    private String content;
    private LocalDateTime timestamp;
    private MessageStatus status;
}