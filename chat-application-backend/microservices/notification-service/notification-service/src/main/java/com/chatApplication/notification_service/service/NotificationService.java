package com.chatApplication.notification_service.service;

import com.chatApplication.notification_service.dto.MessageEvent;
import com.chatApplication.notification_service.dto.NotificationResponse;

import java.util.List;

public interface NotificationService {
    NotificationResponse createNotification(MessageEvent messageEvent);
    List<NotificationResponse> getAllNotifications(String receiverId);
    List<NotificationResponse> getUnreadNotifications(String receiverId);
    NotificationResponse markAsRead(Long notificationId);
    void markAllAsRead(String receiverId);
    void deleteNotification(Long notificationId);
    long getUnreadCount(String receiverId);
}
