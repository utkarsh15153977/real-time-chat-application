package com.chatbackend.chat_application_backend.dto;

import lombok.Data;

@Data
public class AuthResponse {
    private Long id;
    private String token;
    private String name;
    private String username;
    private String email;
    private String phone;
}
