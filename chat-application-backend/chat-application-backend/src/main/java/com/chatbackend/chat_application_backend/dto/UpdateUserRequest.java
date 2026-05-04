package com.chatbackend.chat_application_backend.dto;

import lombok.Data;

@Data
public class UpdateUserRequest {
    private String name;
    private String email;
    private String phone;
    private String profilePicture;
    private String statusMessage;
}
