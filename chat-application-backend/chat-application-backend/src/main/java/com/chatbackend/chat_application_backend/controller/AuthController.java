package com.chatbackend.chat_application_backend.controller;


import com.chatbackend.chat_application_backend.dto.AuthRequest;
import com.chatbackend.chat_application_backend.dto.AuthResponse;
import com.chatbackend.chat_application_backend.dto.RegisterRequest;
import com.chatbackend.chat_application_backend.service.AuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;
    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    //Register User
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@RequestBody RegisterRequest user){
        AuthResponse registeredUser = authService.register(
                user.getName(),
                user.getEmail(),
                user.getPassword(),
                user.getPhone()
        );
        return ResponseEntity.ok(registeredUser);
    }

    //Login User
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody AuthRequest authRequest){
        AuthResponse response = authService.login(
                authRequest.getEmailOrPhone(),
                authRequest.getPassword()
        );

        return ResponseEntity.ok(response);
    }

    // Logout user
    @PostMapping("/logout/{userId}")
    public ResponseEntity<String> logout(@PathVariable Long userId) {
        authService.logout(userId);
        return ResponseEntity.ok("User logged out successfully");
    }
}
