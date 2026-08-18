package com.chatApplication.notification_service.repository;

import com.chatApplication.notification_service.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface NotificationRepository extends
        JpaRepository<Notification, Long> {

    /**
     * Get all notifications of a user.
     */
    List<Notification> findByReceiverIdOrderByCreatedAtDesc(String receiverId);

    /**
     * Get unread notifications.
     */
    List<Notification> findByReceiverIdAndReadStatusFalseOrderByCreatedAtDesc(
            String receiverId
    );

    /**
     * Count unread notifications.
     */
    long countByReceiverIdAndReadStatusFalse(String receiverId);

    /**
     * Delete old notifications.
     */
    void deleteByCreatedAtBefore(LocalDateTime dateTime);
}
