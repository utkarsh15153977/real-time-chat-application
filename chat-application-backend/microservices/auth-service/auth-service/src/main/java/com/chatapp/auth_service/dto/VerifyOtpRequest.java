package com.chatapp.auth_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class VerifyOtpRequest {

    @NotBlank
    private String email;

    @NotBlank
    @Pattern(
            regexp = "\\d{6}",
            message = "OTP must be 6 digits"
    )
    private String otp;
}