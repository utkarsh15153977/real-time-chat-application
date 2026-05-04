package com.chatbackend.chat_application_backend.repository;

import com.chatbackend.chat_application_backend.entity.ChatRoom;
import com.chatbackend.chat_application_backend.entity.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ChatRepository extends JpaRepository<ChatRoom, Long> {
    @Override
    @EntityGraph(attributePaths = "participants")
    Optional<ChatRoom> findById(Long id);

    @EntityGraph(attributePaths = "participants")
    List<ChatRoom> findByParticipantsContaining(User user);
}
