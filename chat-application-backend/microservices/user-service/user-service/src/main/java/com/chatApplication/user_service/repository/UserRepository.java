package com.chatApplication.user_service.repository;

import com.chatApplication.user_service.entity.UserProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<UserProfile,Long> {
    Optional<UserProfile> findByEmail(String email);

    List<UserProfile> findByNameContainingIgnoreCase(
            String name);
}