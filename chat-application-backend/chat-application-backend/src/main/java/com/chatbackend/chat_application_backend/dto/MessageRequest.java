package com.chatbackend.chat_application_backend.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class MessageRequest {
    @NotNull
    private Long senderId;
    @NotNull
    private Long chatRoomId;
    @NotNull
    private String content;
}