package com.chatApplication.chat_service.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ChatRequest {
    @NotNull
    private Long senderId;
    @NotNull
    private Long receiverId;
}
