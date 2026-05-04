package com.chatbackend.chat_application_backend.dto;

import lombok.Data;

@Data
public class UserResponse {
    private Long id;
    private String name;
    private String email;
    private String phone;
    private String profilePicture;
    private boolean online;
    private String statusMessage;

}
