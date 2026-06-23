package com.chatApplication.message_service.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ChatMessage {
    private Long msgId;
    private String senderId;
    private String receiverId;
    private String content;
    private String status;
}
