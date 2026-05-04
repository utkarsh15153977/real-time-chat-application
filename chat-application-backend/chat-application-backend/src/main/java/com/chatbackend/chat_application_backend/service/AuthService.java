package com.chatbackend.chat_application_backend.service;

import com.chatbackend.chat_application_backend.dto.AuthResponse;
import com.chatbackend.chat_application_backend.entity.User;

public interface AuthService {
    AuthResponse register(String name, String email, String password, String phone);
    AuthResponse login(String emailOrPhone, String password);
    void logout(Long userId);
}