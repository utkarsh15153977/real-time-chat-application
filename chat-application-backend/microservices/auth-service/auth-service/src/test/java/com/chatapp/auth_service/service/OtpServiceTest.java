package com.chatapp.auth_service.service;

import com.chatapp.auth_service.config.TestConfig;
import com.chatapp.auth_service.entity.OtpPurpose;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ContextConfiguration(classes = {TestConfig.class})
@ActiveProfiles("test")
class OtpServiceTest {

    @Autowired
    private OtpService otpService;

    @AfterEach
    void cleanup() {

        Long[] testUserIds = {
                100L,
                101L,
                102L,
                103L,
                104L
        };

        for (Long userId : testUserIds) {

            otpService.deleteOtp(
                    userId,
                    OtpPurpose.REGISTRATION
            );

            otpService.deleteOtp(
                    userId,
                    OtpPurpose.LOGIN_2FA
            );
        }
    }

    @Test
    void shouldGenerateAndVerifyOtp() {

        Long userId = 100L;

        String otp = otpService.generateOtp(
                userId,
                OtpPurpose.LOGIN_2FA
        );

        assertNotNull(otp);
        assertEquals(6, otp.length());
        assertTrue(otp.matches("\\d{6}"));

        assertTrue(
                otpService.hasOtp(
                        userId,
                        OtpPurpose.LOGIN_2FA
                )
        );

        boolean verified =
                otpService.verifyOtp(
                        userId,
                        otp,
                        OtpPurpose.LOGIN_2FA
                );

        assertTrue(verified);

        // OTP is deleted after successful verification
        assertFalse(
                otpService.hasOtp(
                        userId,
                        OtpPurpose.LOGIN_2FA
                )
        );
    }

    @Test
    void shouldRejectInvalidOtp() {

        Long userId = 101L;

        String generatedOtp =
                otpService.generateOtp(
                        userId,
                        OtpPurpose.LOGIN_2FA
                );

        assertNotNull(generatedOtp);

        String invalidOtp =
                generatedOtp.equals("000000")
                        ? "000001"
                        : "000000";

        assertFalse(
                otpService.verifyOtp(
                        userId,
                        invalidOtp,
                        OtpPurpose.LOGIN_2FA
                )
        );

        // Invalid OTP must remain in Redis
        assertTrue(
                otpService.hasOtp(
                        userId,
                        OtpPurpose.LOGIN_2FA
                )
        );
    }

    @Test
    void shouldKeepRegistrationAndLoginOtpSeparate() {

        Long userId = 102L;

        String registrationOtp =
                otpService.generateOtp(
                        userId,
                        OtpPurpose.REGISTRATION
                );

        String loginOtp =
                otpService.generateOtp(
                        userId,
                        OtpPurpose.LOGIN_2FA
                );

        assertNotNull(registrationOtp);
        assertNotNull(loginOtp);

        // Both OTPs must exist independently
        assertTrue(
                otpService.hasOtp(
                        userId,
                        OtpPurpose.REGISTRATION
                )
        );

        assertTrue(
                otpService.hasOtp(
                        userId,
                        OtpPurpose.LOGIN_2FA
                )
        );

        // Verify registration OTP
        assertTrue(
                otpService.verifyOtp(
                        userId,
                        registrationOtp,
                        OtpPurpose.REGISTRATION
                )
        );

        // Login OTP must still exist
        assertTrue(
                otpService.hasOtp(
                        userId,
                        OtpPurpose.LOGIN_2FA
                )
        );

        // Verify login OTP
        assertTrue(
                otpService.verifyOtp(
                        userId,
                        loginOtp,
                        OtpPurpose.LOGIN_2FA
                )
        );

        assertFalse(
                otpService.hasOtp(
                        userId,
                        OtpPurpose.REGISTRATION
                )
        );

        assertFalse(
                otpService.hasOtp(
                        userId,
                        OtpPurpose.LOGIN_2FA
                )
        );
    }

    @Test
    void shouldRejectOtpForWrongPurpose() {

        Long userId = 103L;

        String registrationOtp =
                otpService.generateOtp(
                        userId,
                        OtpPurpose.REGISTRATION
                );

        // Registration OTP must NOT work as Login 2FA OTP
        assertFalse(
                otpService.verifyOtp(
                        userId,
                        registrationOtp,
                        OtpPurpose.LOGIN_2FA
                )
        );

        // Registration OTP must still exist
        assertTrue(
                otpService.hasOtp(
                        userId,
                        OtpPurpose.REGISTRATION
                )
        );
    }

    @Test
    void shouldDeleteOtp() {

        Long userId = 104L;

        otpService.generateOtp(
                userId,
                OtpPurpose.REGISTRATION
        );

        assertTrue(
                otpService.hasOtp(
                        userId,
                        OtpPurpose.REGISTRATION
                )
        );

        otpService.deleteOtp(
                userId,
                OtpPurpose.REGISTRATION
        );

        assertFalse(
                otpService.hasOtp(
                        userId,
                        OtpPurpose.REGISTRATION
                )
        );
    }
}