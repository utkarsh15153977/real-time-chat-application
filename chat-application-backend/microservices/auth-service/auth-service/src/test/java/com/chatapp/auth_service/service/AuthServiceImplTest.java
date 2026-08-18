package com.chatapp.auth_service.service;

import com.chatapp.auth_service.dto.AuthResponse;
import com.chatapp.auth_service.entity.User;
import com.chatapp.auth_service.entity.OtpPurpose;
import com.chatapp.auth_service.exception.InvalidCredentialsException;
import com.chatapp.auth_service.repository.UserRepository;
import com.chatapp.auth_service.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private OtpService otpService;

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AuthServiceImpl authService;

    private User user;

    @BeforeEach
    void setUp() {

        user = User.builder()
                .id(100L)
                .name("Test User")
                .email("user@gmail.com")
                .password("encoded-password")
                .phone("9999999999")
                .twoFactorEnabled(true)
                .emailVerified(true)
                .phoneVerified(true)
                .build();
    }

    // =========================================================
    // SUCCESSFUL LOGIN OTP
    // =========================================================

    @Test
    void verifyLoginOtpShouldGenerateJwtAfterSuccessfulOtp()
            throws Exception {

        when(userRepository.findByEmail("user@gmail.com"))
                .thenReturn(Optional.of(user));

        when(
                otpService.verifyOtp(
                        100L,
                        "123456",
                        OtpPurpose.LOGIN_2FA
                )
        ).thenReturn(true);

        when(jwtUtil.generateToken(user))
                .thenReturn("jwt-token");

        AuthResponse response =
                authService.verifyLoginOtp(
                        "user@gmail.com",
                        "123456"
                );

        assertNotNull(response);

        assertEquals(
                "jwt-token",
                response.getToken()
        );

        assertEquals(
                100L,
                response.getUserId()
        );

        assertEquals(
                "user@gmail.com",
                response.getEmail()
        );

        assertFalse(
                response.getRequiresTwoFactor()
        );

        assertNotNull(user.getLastLogin());

        verify(jwtUtil, times(1))
                .generateToken(user);

        verify(otpService, times(1))
                .verifyOtp(
                        100L,
                        "123456",
                        OtpPurpose.LOGIN_2FA
                );

        verify(userRepository, times(1))
                .save(user);
    }


    // =========================================================
    // INVALID OTP
    // =========================================================

    @Test
    void verifyLoginOtpShouldNotGenerateJwtForInvalidOtp() {

        when(userRepository.findByEmail("user@gmail.com"))
                .thenReturn(Optional.of(user));

        when(
                otpService.verifyOtp(
                        100L,
                        "123456",
                        OtpPurpose.LOGIN_2FA
                )
        ).thenReturn(false);

        assertThrows(
                InvalidCredentialsException.class,
                () -> authService.verifyLoginOtp(
                        "user@gmail.com",
                        "123456"
                )
        );

        // VERY IMPORTANT:
        // JWT must never be generated.
        verify(jwtUtil, never())
                .generateToken(any(User.class));

        verify(userRepository, never())
                .save(any(User.class));
    }


    // =========================================================
    // 2FA DISABLED
    // =========================================================

    @Test
    void verifyLoginOtpShouldNotGenerateJwtWhenTwoFactorDisabled() {

        user.setTwoFactorEnabled(false);

        when(userRepository.findByEmail("user@gmail.com"))
                .thenReturn(Optional.of(user));

        assertThrows(
                InvalidCredentialsException.class,
                () -> authService.verifyLoginOtp(
                        "user@gmail.com",
                        "123456"
                )
        );

        verify(otpService, never())
                .verifyOtp(
                        anyLong(),
                        anyString(),
                        any(OtpPurpose.class)
                );

        verify(jwtUtil, never())
                .generateToken(any(User.class));

        verify(userRepository, never())
                .save(any(User.class));
    }


    // =========================================================
    // USER NOT FOUND
    // =========================================================

    @Test
    void verifyLoginOtpShouldNotGenerateJwtWhenUserDoesNotExist() {

        when(userRepository.findByEmail("unknown@gmail.com"))
                .thenReturn(Optional.empty());

        assertThrows(
                RuntimeException.class,
                () -> authService.verifyLoginOtp(
                        "unknown@gmail.com",
                        "123456"
                )
        );

        verify(otpService, never())
                .verifyOtp(
                        anyLong(),
                        anyString(),
                        any(OtpPurpose.class)
                );

        verify(jwtUtil, never())
                .generateToken(any(User.class));
    }


    // =========================================================
    // WRONG OTP PURPOSE
    // =========================================================

    @Test
    void verifyLoginOtpShouldUseLogin2FaPurpose() {

        when(userRepository.findByEmail("user@gmail.com"))
                .thenReturn(Optional.of(user));

        when(
                otpService.verifyOtp(
                        100L,
                        "123456",
                        OtpPurpose.LOGIN_2FA
                )
        ).thenReturn(false);

        assertThrows(
                InvalidCredentialsException.class,
                () -> authService.verifyLoginOtp(
                        "user@gmail.com",
                        "123456"
                )
        );

        // Verify that LOGIN_2FA is specifically used.
        verify(otpService, times(1))
                .verifyOtp(
                        100L,
                        "123456",
                        OtpPurpose.LOGIN_2FA
                );

        verify(jwtUtil, never())
                .generateToken(any(User.class));
    }
}