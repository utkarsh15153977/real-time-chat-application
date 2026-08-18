package com.chatApplication.notification_service.controller;

import com.chatApplication.notification_service.dto.NotificationResponse;
import com.chatApplication.notification_service.service.NotificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
    private final NotificationService notificationService;
    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping("/{receiverId}")
    public ResponseEntity<List<NotificationResponse>> getAllNotifications(
            @PathVariable String receiverId) {
        return ResponseEntity.ok(
                notificationService.getAllNotifications(receiverId));
    }

    @GetMapping("/unread/{receiverId}")
    public ResponseEntity<List<NotificationResponse>> getUnreadNotifications(
            @PathVariable String receiverId) {
        return ResponseEntity.ok(
                notificationService.getUnreadNotifications(receiverId));
    }

    @PutMapping("/{notificationId}/read")
    public ResponseEntity<NotificationResponse> markAsRead(
            @PathVariable Long notificationId) {
        return ResponseEntity.ok(
                notificationService.markAsRead(notificationId));
    }

    @PutMapping("/read-all/{receiverId}")
    public ResponseEntity<String> markAllAsRead(
            @PathVariable String receiverId) {
        notificationService.markAllAsRead(receiverId);
        return ResponseEntity.ok("All notifications marked as read.");
    }

    @DeleteMapping("/{notificationId}")
    public ResponseEntity<String> deleteNotification(
            @PathVariable Long notificationId) {
        notificationService.deleteNotification(notificationId);
        return ResponseEntity.ok("Notification deleted successfully.");
    }

    @GetMapping("/count/{receiverId}")
    public ResponseEntity<Long> getUnreadCount(
            @PathVariable String receiverId) {
        return ResponseEntity.ok(
                notificationService.getUnreadCount(receiverId));
    }
}
