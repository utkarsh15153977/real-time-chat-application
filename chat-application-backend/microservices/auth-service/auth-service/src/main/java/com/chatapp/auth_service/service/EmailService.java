package com.chatapp.auth_service.service;

public interface EmailService {
    void sendOtp(
            String email,
            String otp
    );
}
