package com.chatApplication.message_service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessage {
    private Long msgId;
    private String senderId;
    private String receiverId;
    private String content;
    private String status;
}
