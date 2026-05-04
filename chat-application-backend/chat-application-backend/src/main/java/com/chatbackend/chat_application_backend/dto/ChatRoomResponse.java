package com.chatbackend.chat_application_backend.dto;

import lombok.Data;

import java.util.List;
@Data
public class ChatRoomResponse {
    private Long id;
    private boolean groupChat;
    private String groupName;
    private List<Long> participantIds;
}
