package com.chatApplication.chat_service.dto;

import lombok.Data;

import java.util.List;
@Data
public class GroupRequest {
    private String name;
    private String groupIcon;
    private Long adminId;
    private List<Long> members;
}
