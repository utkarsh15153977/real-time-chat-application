package com.chatApplication.chat_service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for broadcasting transient typing indicators over WebSocket.
 * <p>
 * Published to /topic/chat.{chatRoomId}.typing for room participants.
 * These events are NOT persisted to the database - they are transient
 * and expire automatically via Redis TTL.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TypingEventDTO {
    private Long chatRoomId;
    private Long userId;
    private boolean isTyping;
}
