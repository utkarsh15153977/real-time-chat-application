package com.chatApplication.message_service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupMessageRequest {
    private Long groupId;
    private String senderId;
    private String content;
}