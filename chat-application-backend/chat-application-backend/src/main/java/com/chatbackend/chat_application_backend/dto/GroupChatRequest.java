package com.chatbackend.chat_application_backend.dto;

import lombok.Data;

import java.util.List;
@Data
public class GroupChatRequest {
    private String groupName;
    private List<Long> participantIds;
}
