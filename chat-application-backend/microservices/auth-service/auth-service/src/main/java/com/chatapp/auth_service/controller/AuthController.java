package com.chatapp.auth_service.controller;

import com.chatapp.auth_service.dto.AuthResponse;
import com.chatapp.auth_service.dto.ForgotPasswordRequest;
import com.chatapp.auth_service.dto.LoginRequest;
import com.chatapp.auth_service.dto.RegisterRequest;
import com.chatapp.auth_service.dto.ResetPasswordRequest;
import com.chatapp.auth_service.dto.VerifyOtpRequest;
import com.chatapp.auth_service.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    // =========================================================
    // REGISTER
    // =========================================================

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(
            @Valid @RequestBody RegisterRequest request) {

        return ResponseEntity.ok(
                authService.register(request)
        );
    }


    // =========================================================
    // LOGIN
    // =========================================================

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request) {

        return ResponseEntity.ok(
                authService.login(request)
        );
    }


    // =========================================================
    // VERIFY LOGIN OTP - 2FA
    // =========================================================

    @PostMapping("/verify-otp")
    public ResponseEntity<AuthResponse> verifyOtp(
            @Valid @RequestBody VerifyOtpRequest request) {

        return ResponseEntity.ok(
                authService.verifyLoginOtp(
                        request.getEmail(),
                        request.getOtp()
                )
        );
    }
    // =========================================================
    // FORGOT PASSWORD
    // =========================================================

    @PostMapping("/forgot-password")
    public ResponseEntity<String> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request) {

        return ResponseEntity.ok(
                authService.forgotPassword(
                        request.getEmail()
                )
        );
    }
    // =========================================================
    // RESET PASSWORD
    // =========================================================

    @PostMapping("/reset-password")
    public ResponseEntity<String> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request) {

        return ResponseEntity.ok(
                authService.resetPassword(
                        request.getEmail(),
                        request.getOtp(),
                        request.getNewPassword()
                )
        );
    }
    // =========================================================
    // LOGOUT
    // =========================================================

    @PostMapping("/logout")
    public ResponseEntity<String> logout(
            @RequestHeader("Authorization") String token) {

        return ResponseEntity.ok(
                authService.logout(token)
        );
    }
}