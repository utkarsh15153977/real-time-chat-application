package com.chatapp.auth_service.service;

import com.chatapp.auth_service.dto.AuthResponse;
import com.chatapp.auth_service.dto.LoginRequest;
import com.chatapp.auth_service.dto.RegisterRequest;

public interface AuthService {
    AuthResponse register(RegisterRequest request);
    AuthResponse login(LoginRequest request);
    String logout(String token);
    AuthResponse verifyRegistrationOtp(Long userId, String otp);
    AuthResponse verifyLoginOtp(String email, String otp);
    String forgotPassword(String email);
    String resetPassword(
            String email,
            String otp,
            String newPassword
    );
}