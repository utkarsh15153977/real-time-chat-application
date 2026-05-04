package com.chatbackend.chat_application_backend.repository;

import com.chatbackend.chat_application_backend.entity.Reaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ReactionRepository extends JpaRepository<Reaction, Long> {
    List<Reaction> findByMessage_Id(Long messageId);
    List<Reaction> findByUser_Id(Long userId);
    Optional<Reaction> findByMessage_IdAndUser_Id(Long messageId, Long userId);
    long countByMessage_Id(Long messageId);
}