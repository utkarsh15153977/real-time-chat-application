package com.chatbackend.chat_application_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PresenceEventDTO {
    private Long userId;
    private String status;
}
