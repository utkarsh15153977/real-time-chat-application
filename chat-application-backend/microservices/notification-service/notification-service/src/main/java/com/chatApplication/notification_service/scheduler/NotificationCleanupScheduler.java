package com.chatApplication.notification_service.scheduler;

import com.chatApplication.notification_service.repository.NotificationRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@Slf4j
public class NotificationCleanupScheduler {
    private final NotificationRepository notificationRepository;

    public NotificationCleanupScheduler(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }
    /**
     * Runs every day at 2:00 AM.
     * Deletes notifications older than 30 days.
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void cleanupOldNotifications() {

        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(30);

        notificationRepository.deleteByCreatedAtBefore(cutoffDate);

        log.info("Old notifications deleted successfully. Cutoff Date: {}", cutoffDate);
    }
}
