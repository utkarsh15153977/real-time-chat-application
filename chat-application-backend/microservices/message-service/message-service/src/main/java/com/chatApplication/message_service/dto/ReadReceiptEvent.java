package com.chatApplication.message_service.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ReadReceiptEvent {
    private Long messageId;
    private String senderId;
    private String receiverId;
    private String status;
}
