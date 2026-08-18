package com.chatApplication.notification_service.dto;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MessageEvent {
    private Long messageId;
    private String senderId;
    private String receiverId;
    private String content;
    private String status;
    private LocalDateTime timestamp;
}
