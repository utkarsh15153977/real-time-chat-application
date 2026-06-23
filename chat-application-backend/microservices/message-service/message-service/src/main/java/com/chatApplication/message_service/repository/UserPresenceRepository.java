package com.chatApplication.message_service.repository;

import com.chatApplication.message_service.entity.UserPresence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserPresenceRepository
        extends JpaRepository<UserPresence, String> {
}