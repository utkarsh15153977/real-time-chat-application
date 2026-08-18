package com.chatApplication.notification_service.dto;

import com.chatApplication.notification_service.entity.NotificationType;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class NotificationRequest {
    @NotBlank
    private String senderId;

    @NotBlank
    private String receiverId;

    @NotBlank
    private String title;

    @NotBlank
    private String message;

    private NotificationType notificationType;

}
