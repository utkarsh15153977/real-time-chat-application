package com.chatbackend.chat_application_backend.dto;

import com.chatbackend.chat_application_backend.entity.MessageStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StatusAckDTO {
    private Long messageId;
    private Long chatRoomId;
    private Long userId;
    private MessageStatus status;
    private LocalDateTime timestamp;
}
