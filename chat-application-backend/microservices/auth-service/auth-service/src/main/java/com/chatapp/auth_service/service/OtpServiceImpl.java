package com.chatapp.auth_service.service;

import com.chatapp.auth_service.entity.OtpPurpose;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.concurrent.TimeUnit;

@Service
public class OtpServiceImpl implements OtpService {

    private static final String OTP_KEY_PREFIX = "2FA_OTP:";
    private static final long OTP_EXPIRATION_MINUTES = 5;

    private final RedisTemplate<String, String> redisTemplate;
    private final SecureRandom secureRandom;

    public OtpServiceImpl(
            RedisTemplate<String, String> redisTemplate) {

        this.redisTemplate = redisTemplate;
        this.secureRandom = new SecureRandom();
    }

    // =========================================================
    // GENERATE OTP
    // =========================================================

    @Override
    public String generateOtp(
            Long userId,
            OtpPurpose purpose) {

        validateInput(userId, purpose);

        String otp = String.format(
                "%06d",
                secureRandom.nextInt(1_000_000)
        );

        String key = buildKey(userId, purpose);

        String otpHash = hashOtp(otp);

        redisTemplate.opsForValue().set(
                key,
                otpHash,
                OTP_EXPIRATION_MINUTES,
                TimeUnit.MINUTES
        );

        return otp;
    }

    // =========================================================
    // VERIFY OTP
    // =========================================================

    @Override
    public boolean verifyOtp(
            Long userId,
            String enteredOtp,
            OtpPurpose purpose) {

        if (userId == null
                || purpose == null
                || enteredOtp == null
                || !enteredOtp.matches("\\d{6}")) {

            return false;
        }

        String key = buildKey(userId, purpose);

        String storedHash =
                redisTemplate.opsForValue().get(key);

        if (storedHash == null) {
            return false;
        }

        String enteredOtpHash =
                hashOtp(enteredOtp);

        boolean valid = MessageDigest.isEqual(
                storedHash.getBytes(StandardCharsets.UTF_8),
                enteredOtpHash.getBytes(StandardCharsets.UTF_8)
        );

        if (valid) {
            redisTemplate.delete(key);
        }

        return valid;
    }

    // =========================================================
    // DELETE OTP
    // =========================================================

    @Override
    public void deleteOtp(
            Long userId,
            OtpPurpose purpose) {

        validateInput(userId, purpose);

        String key = buildKey(userId, purpose);

        redisTemplate.delete(key);
    }

    // =========================================================
    // CHECK OTP
    // =========================================================

    @Override
    public boolean hasOtp(
            Long userId,
            OtpPurpose purpose) {

        if (userId == null || purpose == null) {
            return false;
        }

        String key = buildKey(userId, purpose);

        return Boolean.TRUE.equals(
                redisTemplate.hasKey(key)
        );
    }

    // =========================================================
    // BUILD REDIS KEY
    // =========================================================

    private String buildKey(
            Long userId,
            OtpPurpose purpose) {

        return OTP_KEY_PREFIX
                + purpose.name()
                + ":"
                + userId;
    }

    // =========================================================
    // HASH OTP
    // =========================================================

    private String hashOtp(String otp) {

        try {

            MessageDigest digest =
                    MessageDigest.getInstance("SHA-256");

            byte[] hash = digest.digest(
                    otp.getBytes(StandardCharsets.UTF_8)
            );

            StringBuilder hex =
                    new StringBuilder(hash.length * 2);

            for (byte b : hash) {

                hex.append(
                        String.format("%02x", b)
                );
            }

            return hex.toString();

        } catch (NoSuchAlgorithmException e) {

            throw new IllegalStateException(
                    "SHA-256 algorithm not available",
                    e
            );
        }
    }

    // =========================================================
    // VALIDATE INPUT
    // =========================================================

    private void validateInput(
            Long userId,
            OtpPurpose purpose) {

        if (userId == null) {
            throw new IllegalArgumentException(
                    "User ID cannot be null"
            );
        }

        if (purpose == null) {
            throw new IllegalArgumentException(
                    "OTP purpose cannot be null"
            );
        }
    }
}