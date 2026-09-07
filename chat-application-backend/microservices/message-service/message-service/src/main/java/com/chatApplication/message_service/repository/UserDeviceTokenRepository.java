package com.chatApplication.message_service.repository;

import com.chatApplication.message_service.entity.UserDeviceToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserDeviceTokenRepository extends JpaRepository<UserDeviceToken, String> {

    List<UserDeviceToken> findByUserId(String userId);

    Optional<UserDeviceToken> findByFcmToken(String fcmToken);

    void deleteByFcmToken(String fcmToken);

    boolean existsByFcmToken(String fcmToken);
}
