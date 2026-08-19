package com.chatapp.auth_service.service;

import com.chatapp.auth_service.entity.Otp;
import com.chatapp.auth_service.entity.OtpPurpose;
import com.chatapp.auth_service.repository.OtpRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
@Transactional
public class OtpServiceImpl implements OtpService {
    private final OtpRepository otpRepository;
    private final SecureRandom secureRandom = new SecureRandom();
    public OtpServiceImpl(OtpRepository otpRepository) {
        this.otpRepository = otpRepository;
    }
    @Override
    public String generateOtp(
            Long userId,
            OtpPurpose purpose
    ) {
        // Delete any previous OTP for this user and purpose
        otpRepository.deleteByUserIdAndPurpose(
                userId,
                purpose
        );
        // Generate a random 6-digit OTP
        String otp = String.format(
                "%06d",
                secureRandom.nextInt(1_000_000)
        );
        // Determine OTP expiry time
        int expiryMinutes;
        switch (purpose) {
            // Registration / verification OTPs
            case REGISTRATION:
            case REGISTRATION_EMAIL:
            case REGISTRATION_PHONE:
            case VERIFY_EMAIL:
            case VERIFY_PHONE:
                expiryMinutes = 15;
                break;
            // Login / password OTPs
            case LOGIN_2FA:
            case PASSWORD_RESET:
            case FORGOT_PASSWORD:
            case RESET_PASSWORD:
                expiryMinutes = 5;
                break;
            default:
                throw new IllegalArgumentException(
                        "Unsupported OTP purpose: " + purpose
                );
        }
        // Create OTP entity
        Otp otpEntity = Otp.builder()
                .userId(userId)
                .otp(otp)
                .purpose(purpose)
                .expiresAt(
                        LocalDateTime.now()
                                .plusMinutes(expiryMinutes)
                )
                .used(false)
                .build();

        // Save OTP
        otpRepository.save(otpEntity);
        return otp;
    }
    @Override
    public boolean verifyOtp(
            Long userId,
            String enteredOtp,
            OtpPurpose purpose
    ) {
        Optional<Otp> optionalOtp =
                otpRepository
                        .findTopByUserIdAndPurposeOrderByCreatedAtDesc(
                                userId,
                                purpose
                        );
        if (optionalOtp.isEmpty()) {
            return false;
        }
        Otp otp = optionalOtp.get();
        // Check OTP value, expiry and used status
        if (!otp.isValid(enteredOtp)) {
            return false;
        }
        // Mark OTP as used
        otp.setUsed(true);
        otpRepository.save(otp);
        return true;
    }
    @Override
    public void deleteOtp(
            Long userId,
            OtpPurpose purpose
    ) {
        otpRepository.deleteByUserIdAndPurpose(
                userId,
                purpose
        );
    }
    @Override
    public boolean hasOtp(
            Long userId,
            OtpPurpose purpose
    ) {
        return otpRepository.existsByUserIdAndPurposeAndUsedFalse(
                userId,
                purpose
        );
    }
}