package com.chatApplication.message_service.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class MessageResponse {
    private Long msgId;
    private String senderId;
    private String receiverId;
    private String content;
    private LocalDateTime sendTime;
    private boolean delivered;
    private boolean seen;
}
