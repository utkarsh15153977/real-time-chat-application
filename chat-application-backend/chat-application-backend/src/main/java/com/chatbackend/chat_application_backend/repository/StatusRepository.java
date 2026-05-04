package com.chatbackend.chat_application_backend.repository;

import com.chatbackend.chat_application_backend.entity.Status;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface StatusRepository extends JpaRepository<Status, Long> {
    List<Status> findByUserId(Long userId);
    List<Status> findByExpiresAtAfter(LocalDateTime now);
}
