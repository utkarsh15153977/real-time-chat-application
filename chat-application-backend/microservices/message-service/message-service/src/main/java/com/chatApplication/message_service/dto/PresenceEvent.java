package com.chatApplication.message_service.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PresenceEvent {
    private String userId;
    private String status;
}
