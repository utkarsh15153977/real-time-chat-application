package com.chatapp.auth_service.entity;

public enum OtpPurpose {
    REGISTRATION,
    LOGIN_2FA,
    PASSWORD_RESET,
    REGISTRATION_EMAIL,
    REGISTRATION_PHONE,
    FORGOT_PASSWORD,
    RESET_PASSWORD,
    VERIFY_EMAIL,
    VERIFY_PHONE
}