package com.chatApplication.notification_service.dto;

import com.chatApplication.notification_service.entity.NotificationType;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationResponse {
    private Long notificationId;
    private String senderId;
    private String receiverId;
    private String title;
    private String message;
    private NotificationType notificationType;
    private Boolean readStatus;
    private LocalDateTime createdAt;
}
