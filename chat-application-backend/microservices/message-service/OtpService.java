package com.chatapp.auth_service.service;

public interface OtpService {
    String generateOtp(Long userId);
    boolean verifyOtp(Long userId, String enteredOtp);
    void deleteOtp(Long userId);
    boolean hasOtp(Long userId);
}
