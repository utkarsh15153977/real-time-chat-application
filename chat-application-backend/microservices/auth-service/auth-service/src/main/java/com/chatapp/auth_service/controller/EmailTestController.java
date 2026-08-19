package com.chatapp.auth_service.controller;

import com.chatapp.auth_service.entity.OtpPurpose;
import com.chatapp.auth_service.service.EmailService;
import com.chatapp.auth_service.service.OtpService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/test")
public class EmailTestController {
    private final EmailService emailService;
    private final OtpService otpService;
    public EmailTestController(
            EmailService emailService,
            OtpService otpService) {
        this.emailService = emailService;
        this.otpService = otpService;
    }
    @PostMapping("/send-email")
    public ResponseEntity<String> sendEmail(
            @RequestParam Long userId,
            @RequestParam String email) {
        // Generate OTP and save it in database
        String otp = otpService.generateOtp(
                userId,
                OtpPurpose.REGISTRATION_EMAIL
        );
        // Send the SAME OTP through email
        emailService.sendOtp(
                email,
                otp
        );
        return ResponseEntity.ok(
                "OTP generated, saved and sent successfully"
        );
    }
}