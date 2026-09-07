package com.chatApplication.notification_service.scheduler;

import com.chatApplication.notification_service.repository.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationCleanupSchedulerTest {

    @Mock
    private NotificationRepository notificationRepository;

    @InjectMocks
    private NotificationCleanupScheduler scheduler;

    @Test
    void cleanupOldNotifications_shouldCallDeleteByCreatedAtBefore() {
        scheduler.cleanupOldNotifications();

        verify(notificationRepository, times(1)).deleteByCreatedAtBefore(any());
    }
}
