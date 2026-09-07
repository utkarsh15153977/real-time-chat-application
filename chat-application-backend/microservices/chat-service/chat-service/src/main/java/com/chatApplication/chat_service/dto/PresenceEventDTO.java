package com.chatApplication.chat_service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for broadcasting presence (online/offline) events over WebSocket.
 * <p>
 * Published to /topic/presence for all connected clients to consume.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PresenceEventDTO {
    private Long userId;
    private String status; // "ONLINE" or "OFFLINE"
}
