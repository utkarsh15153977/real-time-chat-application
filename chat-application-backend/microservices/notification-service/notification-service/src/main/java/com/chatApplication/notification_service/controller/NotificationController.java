package com.chatApplication.notification_service.controller;

import com.chatApplication.notification_service.dto.NotificationResponse;
import com.chatApplication.notification_service.exception.AccessDeniedException;
import com.chatApplication.notification_service.service.NotificationService;
import jakarta.servlet.http.HttpServletRequest;
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

    /**
     * Extracts the authenticated user identity from the trusted X-User-Id header
     * injected by the API Gateway after JWT validation.
     */
    private String getAuthenticatedUserId(HttpServletRequest request) {
        String userId = request.getHeader("X-User-Id");
        if (userId == null || userId.isBlank()) {
            throw new AccessDeniedException("Unable to determine authenticated user identity");
        }
        return userId;
    }

    @GetMapping("/{receiverId}")
    public ResponseEntity<List<NotificationResponse>> getAllNotifications(
            @PathVariable String receiverId,
            HttpServletRequest request) {
        String authenticatedUserId = getAuthenticatedUserId(request);
        return ResponseEntity.ok(
                notificationService.getAllNotifications(authenticatedUserId));
    }

    @GetMapping("/unread/{receiverId}")
    public ResponseEntity<List<NotificationResponse>> getUnreadNotifications(
            @PathVariable String receiverId,
            HttpServletRequest request) {
        String authenticatedUserId = getAuthenticatedUserId(request);
        return ResponseEntity.ok(
                notificationService.getUnreadNotifications(authenticatedUserId));
    }

    @PutMapping("/{notificationId}/read")
    public ResponseEntity<NotificationResponse> markAsRead(
            @PathVariable Long notificationId,
            HttpServletRequest request) {
        String authenticatedUserId = getAuthenticatedUserId(request);
        return ResponseEntity.ok(
                notificationService.markAsRead(notificationId, authenticatedUserId));
    }

    @PutMapping("/read-all/{receiverId}")
    public ResponseEntity<String> markAllAsRead(
            @PathVariable String receiverId,
            HttpServletRequest request) {
        String authenticatedUserId = getAuthenticatedUserId(request);
        notificationService.markAllAsRead(authenticatedUserId);
        return ResponseEntity.ok("All notifications marked as read.");
    }

    @DeleteMapping("/{notificationId}")
    public ResponseEntity<String> deleteNotification(
            @PathVariable Long notificationId,
            HttpServletRequest request) {
        String authenticatedUserId = getAuthenticatedUserId(request);
        notificationService.deleteNotification(notificationId, authenticatedUserId);
        return ResponseEntity.ok("Notification deleted successfully.");
    }

    @GetMapping("/count/{receiverId}")
    public ResponseEntity<Long> getUnreadCount(
            @PathVariable String receiverId,
            HttpServletRequest request) {
        String authenticatedUserId = getAuthenticatedUserId(request);
        return ResponseEntity.ok(
                notificationService.getUnreadCount(authenticatedUserId));
    }
}
