package com.chatapp.auth_service.repository;

import com.chatapp.auth_service.entity.Otp;
import com.chatapp.auth_service.entity.OtpPurpose;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OtpRepository extends JpaRepository<Otp, Long> {
    Optional<Otp> findTopByUserIdAndPurposeOrderByCreatedAtDesc(
            Long userId,
            OtpPurpose purpose
    );

    void deleteByUserIdAndPurpose(
            Long userId,
            OtpPurpose purpose
    );

    boolean existsByUserIdAndPurpose(
            Long userId,
            OtpPurpose purpose
    );
}
