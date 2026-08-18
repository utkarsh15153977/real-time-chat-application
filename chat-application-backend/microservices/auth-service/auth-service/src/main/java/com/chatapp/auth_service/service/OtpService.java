package com.chatapp.auth_service.service;

import com.chatapp.auth_service.entity.OtpPurpose;

public interface OtpService {

    String generateOtp(
            Long userId,
            OtpPurpose purpose
    );

    boolean verifyOtp(
            Long userId,
            String enteredOtp,
            OtpPurpose purpose
    );

    void deleteOtp(
            Long userId,
            OtpPurpose purpose
    );

    boolean hasOtp(
            Long userId,
            OtpPurpose purpose
    );
}