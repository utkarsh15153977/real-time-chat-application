package com.chatapp.auth_service.repository;

import com.chatapp.auth_service.entity.Otp;
import com.chatapp.auth_service.entity.OtpPurpose;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OtpRepository extends JpaRepository<Otp, Long> {

    void deleteByUserIdAndPurpose(
            Long userId,
            OtpPurpose purpose
    );

    Optional<Otp> findTopByUserIdAndPurposeOrderByCreatedAtDesc(
            Long userId,
            OtpPurpose purpose
    );

    boolean existsByUserIdAndPurposeAndUsedFalse(
            Long userId,
            OtpPurpose purpose
    );
}