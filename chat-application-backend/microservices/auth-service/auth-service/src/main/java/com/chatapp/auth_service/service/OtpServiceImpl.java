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

        // Remove previous OTP for the same purpose
        otpRepository.deleteByUserIdAndPurpose(
                userId,
                purpose
        );

        // Generate 6-digit OTP
        String otp = String.format(
                "%06d",
                secureRandom.nextInt(1_000_000)
        );

        // Determine OTP expiry
        int expiryMinutes = switch (purpose) {

            // Registration / verification OTPs
            case REGISTRATION,
                 REGISTRATION_EMAIL,
                 REGISTRATION_PHONE,
                 VERIFY_EMAIL,
                 VERIFY_PHONE -> 15;

            // Login / password OTPs
            case LOGIN_2FA,
                 PASSWORD_RESET,
                 FORGOT_PASSWORD,
                 RESET_PASSWORD -> 5;
        };

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

        return otpRepository.existsByUserIdAndPurpose(
                userId,
                purpose
        );
    }
}