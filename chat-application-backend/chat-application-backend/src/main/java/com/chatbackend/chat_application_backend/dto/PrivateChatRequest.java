package com.chatbackend.chat_application_backend.dto;

import lombok.Data;

@Data
public class PrivateChatRequest {
    private Long user1Id;
    private Long user2Id;
}
