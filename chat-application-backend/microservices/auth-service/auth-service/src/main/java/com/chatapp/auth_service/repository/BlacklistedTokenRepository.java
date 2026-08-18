package com.chatapp.auth_service.repository;

import com.chatapp.auth_service.entity.BlacklistedToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

public interface BlacklistedTokenRepository extends JpaRepository<BlacklistedToken, Long> {
    boolean existsByToken(String token);
    Optional<BlacklistedToken> findByToken(String token);
    void deleteByBlacklistedAtBefore(LocalDateTime dateTime);
    void deleteByToken(String token);
}
