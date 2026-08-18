package com.chatapp.auth_service.controller;

import com.chatapp.auth_service.dto.AuthResponse;
import com.chatapp.auth_service.dto.LoginRequest;
import com.chatapp.auth_service.dto.VerifyOtpRequest;
import com.chatapp.auth_service.exception.InvalidCredentialsException;
import com.chatapp.auth_service.repository.BlacklistedTokenRepository;
import com.chatapp.auth_service.security.JwtUtil;
import com.chatapp.auth_service.service.AuthService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthServiceTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AuthService authService;

    /*
     * JwtAuthenticationFilter dependencies
     */
    @MockitoBean
    private JwtUtil jwtUtil;

    @MockitoBean
    private BlacklistedTokenRepository blacklistedTokenRepository;


    // =========================================================
    // LOGIN WITH 2FA
    // =========================================================

    @Test
    void loginShouldNotReturnJwtWhenTwoFactorIsRequired()
            throws Exception {

        LoginRequest request = new LoginRequest();

        request.setEmail("user@gmail.com");
        request.setPassword("Password@123");

        AuthResponse response = AuthResponse.builder()
                .token(null)
                .userId(100L)
                .name("Test User")
                .email("user@gmail.com")
                .message("OTP required for login")
                .requiresTwoFactor(true)
                .build();

        when(authService.login(any(LoginRequest.class)))
                .thenReturn(response);

        mockMvc.perform(
                        post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(request)
                                )
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").doesNotExist())
                .andExpect(
                        jsonPath("$.requiresTwoFactor")
                                .value(true)
                )
                .andExpect(
                        jsonPath("$.message")
                                .value("OTP required for login")
                );

        verify(authService, times(1))
                .login(any(LoginRequest.class));
    }


    // =========================================================
    // VERIFY OTP SUCCESS
    // =========================================================

    @Test
    void verifyOtpShouldReturnJwtAfterSuccessfulVerification()
            throws Exception {

        VerifyOtpRequest request = new VerifyOtpRequest();

        request.setEmail("user@gmail.com");
        request.setOtp("123456");

        AuthResponse response = AuthResponse.builder()
                .token("jwt-token-generated-after-2fa")
                .userId(100L)
                .name("Test User")
                .email("user@gmail.com")
                .message("Login successful")
                .requiresTwoFactor(false)
                .build();

        when(
                authService.verifyLoginOtp(
                        "user@gmail.com",
                        "123456"
                )
        ).thenReturn(response);

        mockMvc.perform(
                        post("/api/auth/verify-otp")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(request)
                                )
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.token")
                                .value("jwt-token-generated-after-2fa")
                )
                .andExpect(
                        jsonPath("$.requiresTwoFactor")
                                .value(false)
                )
                .andExpect(
                        jsonPath("$.message")
                                .value("Login successful")
                );

        verify(authService, times(1))
                .verifyLoginOtp(
                        "user@gmail.com",
                        "123456"
                );
    }


    // =========================================================
    // INVALID OTP
    // =========================================================

    @Test
    void verifyOtpShouldRejectInvalidOtp()
            throws Exception {

        VerifyOtpRequest request = new VerifyOtpRequest();

        request.setEmail("user@gmail.com");
        request.setOtp("123456");

        when(
                authService.verifyLoginOtp(
                        "user@gmail.com",
                        "123456"
                )
        ).thenThrow(
                new InvalidCredentialsException(
                        "Invalid or expired OTP"
                )
        );

        mockMvc.perform(
                        post("/api/auth/verify-otp")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(request)
                                )
                )
                .andExpect(status().isUnauthorized());

        verify(authService, times(1))
                .verifyLoginOtp(
                        "user@gmail.com",
                        "123456"
                );
    }


    // =========================================================
    // OTP MUST BE SIX DIGITS
    // =========================================================

    @Test
    void verifyOtpShouldRejectInvalidOtpFormat()
            throws Exception {

        VerifyOtpRequest request = new VerifyOtpRequest();

        request.setEmail("user@gmail.com");
        request.setOtp("123");

        mockMvc.perform(
                        post("/api/auth/verify-otp")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(request)
                                )
                )
                .andExpect(status().isBadRequest());

        verify(
                authService,
                never()
        ).verifyLoginOtp(
                anyString(),
                anyString()
        );
    }
}