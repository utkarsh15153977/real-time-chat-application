package com.chatbackend.chat_application_backend.dto;

import lombok.Data;

@Data
public class AuthRequest {
    private String emailOrPhone;
    private String password;
}