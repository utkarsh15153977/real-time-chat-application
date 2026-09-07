package com.chatapp.auth_service.controller;

import com.chatapp.auth_service.config.TestConfig;
import com.chatapp.auth_service.entity.OtpPurpose;
import com.chatapp.auth_service.entity.User;
import com.chatapp.auth_service.repository.OtpRepository;
import com.chatapp.auth_service.repository.UserRepository;
import com.chatapp.auth_service.security.JwtUtil;
import com.chatapp.auth_service.service.OtpService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ContextConfiguration(classes = {TestConfig.class})
@Transactional
class ForgotPasswordIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OtpRepository otpRepository;

    @Autowired
    private OtpService otpService;

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
        otpRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void forgotPasswordWithValidEmailShouldGenerateOtp() throws Exception {
        User user = createUser("Forgot Password User", "forgot-test@gmail.com", "9999999999", "OldPassword@123");

        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType("application/json")
                        .content("""
                                {
                                    "email": "forgot-test@gmail.com"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(content().string("Password reset OTP generated successfully"));

        assertTrue(otpService.hasOtp(user.getId(), OtpPurpose.PASSWORD_RESET),
                "Password reset OTP should exist");
    }

    @Test
    void forgotPasswordWithUnknownEmailShouldReturnNotFound() throws Exception {
        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType("application/json")
                        .content("""
                                {
                                    "email": "unknown@gmail.com"
                                }
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    void forgotPasswordWithBlankEmailShouldBeRejected() throws Exception {
        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType("application/json")
                        .content("""
                                {
                                    "email": ""
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void resetPasswordWithValidOtpShouldChangePassword() throws Exception {
        User user = createUser("Reset Password User", "reset-test@gmail.com", "8888888888", "OldPassword@123");
        String oldPassword = user.getPassword();
        String otp = otpService.generateOtp(user.getId(), OtpPurpose.PASSWORD_RESET);

        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType("application/json")
                        .content("""
                                {
                                    "email": "reset-test@gmail.com",
                                    "otp": "%s",
                                    "newPassword": "NewPassword@123"
                                }
                                """.formatted(otp)))
                .andExpect(status().isOk())
                .andExpect(content().string("Password reset successful"));

        User updatedUser = userRepository.findByEmail("reset-test@gmail.com").orElseThrow();
        assertNotEquals(oldPassword, updatedUser.getPassword());
        assertTrue(passwordEncoder.matches("NewPassword@123", updatedUser.getPassword()));
    }

    @Test
    void resetPasswordWithInvalidOtpShouldBeUnauthorized() throws Exception {
        createUser("Invalid OTP User", "invalid-otp@gmail.com", "7777777777", "OldPassword@123");

        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType("application/json")
                        .content("""
                                {
                                    "email": "invalid-otp@gmail.com",
                                    "otp": "123456",
                                    "newPassword": "NewPassword@123"
                                }
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void resetPasswordWithExpiredOtpShouldBeUnauthorized() throws Exception {
        User user = createUser("Expired OTP User", "expired-otp@gmail.com", "6666666666", "OldPassword@123");
        otpService.generateOtp(user.getId(), OtpPurpose.PASSWORD_RESET);

        // Simulate OTP expiration by deleting all OTPs for this user
        otpRepository.deleteByUserIdAndPurpose(user.getId(), OtpPurpose.PASSWORD_RESET);

        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType("application/json")
                        .content("""
                                {
                                    "email": "expired-otp@gmail.com",
                                    "otp": "123456",
                                    "newPassword": "NewPassword@123"
                                }
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void resetPasswordWithInvalidNewPasswordShouldBeRejected() throws Exception {
        User user = createUser("Invalid Password User", "invalid-password@gmail.com", "5555555555", "OldPassword@123");
        String otp = otpService.generateOtp(user.getId(), OtpPurpose.PASSWORD_RESET);

        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType("application/json")
                        .content("""
                                {
                                    "email": "invalid-password@gmail.com",
                                    "otp": "%s",
                                    "newPassword": "password"
                                }
                                """.formatted(otp)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void otpShouldOnlyBeUsedOnce() throws Exception {
        User user = createUser("OTP Once User", "otp-once@gmail.com", "4444444444", "OldPassword@123");
        String otp = otpService.generateOtp(user.getId(), OtpPurpose.PASSWORD_RESET);

        // First reset
        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType("application/json")
                        .content("""
                                {
                                    "email": "otp-once@gmail.com",
                                    "otp": "%s",
                                    "newPassword": "NewPassword@123"
                                }
                                """.formatted(otp)))
                .andExpect(status().isOk());

        assertFalse(otpService.hasOtp(user.getId(), OtpPurpose.PASSWORD_RESET));

        // Second reset using same OTP
        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType("application/json")
                        .content("""
                                {
                                    "email": "otp-once@gmail.com",
                                    "otp": "%s",
                                    "newPassword": "AnotherPassword@123"
                                }
                                """.formatted(otp)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void oldPasswordShouldNotWorkAfterReset() throws Exception {
        String oldPassword = "OldPassword@123";
        String newPassword = "NewPassword@123";
        User user = createUser("Old Password User", "old-password@gmail.com", "3333333333", oldPassword);
        String otp = otpService.generateOtp(user.getId(), OtpPurpose.PASSWORD_RESET);

        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType("application/json")
                        .content("""
                                {
                                    "email": "old-password@gmail.com",
                                    "otp": "%s",
                                    "newPassword": "%s"
                                }
                                """.formatted(otp, newPassword)))
                .andExpect(status().isOk());

        User updatedUser = userRepository.findByEmail("old-password@gmail.com").orElseThrow();
        assertFalse(passwordEncoder.matches(oldPassword, updatedUser.getPassword()), "Old password should no longer work");
        assertTrue(passwordEncoder.matches(newPassword, updatedUser.getPassword()), "New password should work");
    }

    private User createUser(String name, String email, String phone, String password) {
        User user = User.builder()
                .name(name)
                .email(email)
                .password(passwordEncoder.encode(password))
                .phone(phone)
                .emailVerified(true)
                .phoneVerified(true)
                .twoFactorEnabled(false)
                .online(false)
                .build();
        return userRepository.save(user);
    }
}
