package com.chatApplication.message_service.kafka;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageEvent {
    private Long messageId;
    private String senderId;
    private String receiverId;
    private String content;
    private String status;
}
