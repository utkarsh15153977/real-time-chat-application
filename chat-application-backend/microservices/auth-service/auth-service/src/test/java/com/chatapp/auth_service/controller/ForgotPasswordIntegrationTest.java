package com.chatapp.auth_service.controller;

import com.chatapp.auth_service.entity.OtpPurpose;
import com.chatapp.auth_service.entity.User;
import com.chatapp.auth_service.repository.UserRepository;
import com.chatapp.auth_service.security.JwtUtil;
import com.chatapp.auth_service.service.OtpService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ForgotPasswordIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OtpService otpService;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();

        // Clean test users
        userRepository.deleteAll();

        // Clean password reset OTP keys
        var keys = redisTemplate.keys("2FA_OTP:PASSWORD_RESET:*");

        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }


    // =========================================================
    // 1. FORGOT PASSWORD - VALID EMAIL
    // =========================================================

    @Test
    void forgotPasswordWithValidEmailShouldGenerateOtp()
            throws Exception {

        User user = createUser(
                "Forgot Password User",
                "forgot-test@gmail.com",
                "9999999999",
                "OldPassword@123"
        );

        mockMvc.perform(
                        post("/api/auth/forgot-password")
                                .contentType("application/json")
                                .content("""
                                        {
                                            "email": "forgot-test@gmail.com"
                                        }
                                        """)
                )
                .andExpect(status().isOk())
                .andExpect(
                        content().string(
                                "Password reset OTP generated successfully"
                        )
                );

        assertTrue(
                otpService.hasOtp(
                        user.getId(),
                        OtpPurpose.PASSWORD_RESET
                ),
                "Password reset OTP should exist in Redis"
        );
    }


    // =========================================================
    // 2. FORGOT PASSWORD - UNKNOWN EMAIL
    // =========================================================

    @Test
    void forgotPasswordWithUnknownEmailShouldReturnNotFound()
            throws Exception {

        mockMvc.perform(
                        post("/api/auth/forgot-password")
                                .contentType("application/json")
                                .content("""
                                        {
                                            "email": "unknown@gmail.com"
                                        }
                                        """)
                )
                .andExpect(status().isNotFound());
    }


    // =========================================================
    // 3. FORGOT PASSWORD - BLANK EMAIL
    // =========================================================

    @Test
    void forgotPasswordWithBlankEmailShouldBeRejected()
            throws Exception {

        mockMvc.perform(
                        post("/api/auth/forgot-password")
                                .contentType("application/json")
                                .content("""
                                        {
                                            "email": ""
                                        }
                                        """)
                )
                .andExpect(status().isBadRequest());
    }


    // =========================================================
    // 4. RESET PASSWORD - VALID OTP
    // =========================================================

    @Test
    void resetPasswordWithValidOtpShouldChangePassword()
            throws Exception {

        User user = createUser(
                "Reset Password User",
                "reset-test@gmail.com",
                "8888888888",
                "OldPassword@123"
        );

        String oldPassword = user.getPassword();

        String otp = otpService.generateOtp(
                user.getId(),
                OtpPurpose.PASSWORD_RESET
        );

        mockMvc.perform(
                        post("/api/auth/reset-password")
                                .contentType("application/json")
                                .content("""
                                        {
                                            "email": "reset-test@gmail.com",
                                            "otp": "%s",
                                            "newPassword": "NewPassword@123"
                                        }
                                        """.formatted(otp))
                )
                .andExpect(status().isOk())
                .andExpect(
                        content().string(
                                "Password reset successful"
                        )
                );

        User updatedUser = userRepository
                .findByEmail("reset-test@gmail.com")
                .orElseThrow();

        assertNotEquals(
                oldPassword,
                updatedUser.getPassword()
        );

        assertTrue(
                passwordEncoder.matches(
                        "NewPassword@123",
                        updatedUser.getPassword()
                )
        );
    }


    // =========================================================
    // 5. RESET PASSWORD - INVALID OTP
    // =========================================================

    @Test
    void resetPasswordWithInvalidOtpShouldBeUnauthorized()
            throws Exception {

        User user = createUser(
                "Invalid OTP User",
                "invalid-otp@gmail.com",
                "7777777777",
                "OldPassword@123"
        );

        otpService.generateOtp(
                user.getId(),
                OtpPurpose.PASSWORD_RESET
        );

        mockMvc.perform(
                        post("/api/auth/reset-password")
                                .contentType("application/json")
                                .content("""
                                        {
                                            "email": "invalid-otp@gmail.com",
                                            "otp": "123456",
                                            "newPassword": "NewPassword@123"
                                        }
                                        """)
                )
                .andExpect(status().isUnauthorized());
    }


    // =========================================================
    // 6. RESET PASSWORD - EXPIRED OTP
    // =========================================================

    @Test
    void resetPasswordWithExpiredOtpShouldBeUnauthorized()
            throws Exception {

        User user = createUser(
                "Expired OTP User",
                "expired-otp@gmail.com",
                "6666666666",
                "OldPassword@123"
        );

        otpService.generateOtp(
                user.getId(),
                OtpPurpose.PASSWORD_RESET
        );

        /*
         * Simulate OTP expiration.
         *
         * We don't wait 5 minutes during a test.
         * Instead, delete the Redis key to represent
         * an expired OTP.
         */
        var keys = redisTemplate.keys(
                "2FA_OTP:PASSWORD_RESET:" + user.getId()
        );

        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }

        mockMvc.perform(
                        post("/api/auth/reset-password")
                                .contentType("application/json")
                                .content("""
                                        {
                                            "email": "expired-otp@gmail.com",
                                            "otp": "123456",
                                            "newPassword": "NewPassword@123"
                                        }
                                        """)
                )
                .andExpect(status().isUnauthorized());
    }


    // =========================================================
    // 7. RESET PASSWORD - INVALID NEW PASSWORD
    // =========================================================

    @Test
    void resetPasswordWithInvalidNewPasswordShouldBeRejected()
            throws Exception {

        User user = createUser(
                "Invalid Password User",
                "invalid-password@gmail.com",
                "5555555555",
                "OldPassword@123"
        );

        String otp = otpService.generateOtp(
                user.getId(),
                OtpPurpose.PASSWORD_RESET
        );

        mockMvc.perform(
                        post("/api/auth/reset-password")
                                .contentType("application/json")
                                .content("""
                                        {
                                            "email": "invalid-password@gmail.com",
                                            "otp": "%s",
                                            "newPassword": "password"
                                        }
                                        """.formatted(otp))
                )
                .andExpect(status().isBadRequest());
    }


    // =========================================================
    // 8. OTP CAN ONLY BE USED ONCE
    // =========================================================

    @Test
    void otpShouldOnlyBeUsedOnce()
            throws Exception {

        User user = createUser(
                "OTP Once User",
                "otp-once@gmail.com",
                "4444444444",
                "OldPassword@123"
        );

        String otp = otpService.generateOtp(
                user.getId(),
                OtpPurpose.PASSWORD_RESET
        );

        // First reset
        mockMvc.perform(
                        post("/api/auth/reset-password")
                                .contentType("application/json")
                                .content("""
                                        {
                                            "email": "otp-once@gmail.com",
                                            "otp": "%s",
                                            "newPassword": "NewPassword@123"
                                        }
                                        """.formatted(otp))
                )
                .andExpect(status().isOk());

        // OTP should now be deleted
        assertFalse(
                otpService.hasOtp(
                        user.getId(),
                        OtpPurpose.PASSWORD_RESET
                )
        );

        // Second reset using same OTP
        mockMvc.perform(
                        post("/api/auth/reset-password")
                                .contentType("application/json")
                                .content("""
                                        {
                                            "email": "otp-once@gmail.com",
                                            "otp": "%s",
                                            "newPassword": "AnotherPassword@123"
                                        }
                                        """.formatted(otp))
                )
                .andExpect(status().isUnauthorized());
    }


    // =========================================================
    // 9. OLD PASSWORD SHOULD NO LONGER WORK
    // =========================================================

    @Test
    void oldPasswordShouldNotWorkAfterReset()
            throws Exception {

        String oldPassword = "OldPassword@123";
        String newPassword = "NewPassword@123";

        User user = createUser(
                "Old Password User",
                "old-password@gmail.com",
                "3333333333",
                oldPassword
        );

        String otp = otpService.generateOtp(
                user.getId(),
                OtpPurpose.PASSWORD_RESET
        );

        // Reset password
        mockMvc.perform(
                        post("/api/auth/reset-password")
                                .contentType("application/json")
                                .content("""
                                        {
                                            "email": "old-password@gmail.com",
                                            "otp": "%s",
                                            "newPassword": "%s"
                                        }
                                        """.formatted(
                                        otp,
                                        newPassword
                                ))
                )
                .andExpect(status().isOk());

        User updatedUser = userRepository
                .findByEmail("old-password@gmail.com")
                .orElseThrow();

        // Old password must no longer match
        assertFalse(
                passwordEncoder.matches(
                        oldPassword,
                        updatedUser.getPassword()
                ),
                "Old password should no longer work"
        );

        // New password must match
        assertTrue(
                passwordEncoder.matches(
                        newPassword,
                        updatedUser.getPassword()
                ),
                "New password should work"
        );
    }


    // =========================================================
    // HELPER METHOD
    // =========================================================

    private User createUser(
            String name,
            String email,
            String phone,
            String password) {

        User user = User.builder()
                .name(name)
                .email(email)
                .password(
                        passwordEncoder.encode(password)
                )
                .phone(phone)
                .emailVerified(true)
                .phoneVerified(true)
                .twoFactorEnabled(false)
                .online(false)
                .build();

        return userRepository.save(user);
    }
}