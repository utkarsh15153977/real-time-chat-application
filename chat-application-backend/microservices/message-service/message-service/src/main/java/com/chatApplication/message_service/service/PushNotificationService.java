package com.chatApplication.message_service.service;

import com.chatApplication.message_service.dto.DeviceTokenRequestDTO;

import java.util.concurrent.CompletableFuture;

/**
 * Service for managing FCM device tokens and dispatching push notifications.
 * <p>
 * Push notifications are sent to offline recipients when a new message arrives
 * via WebSocket. The service manages the full lifecycle:
 * <ul>
 *   <li>Device token registration and deduplication</li>
 *   <li>Device token unregistration</li>
 *   <li>Asynchronous FCM multicast dispatch</li>
 *   <li>Automatic cleanup of invalid/expired tokens</li>
 * </ul>
 * <p>
 * If Firebase is not configured (dry-run mode), all push dispatches are silently skipped.
 */
public interface PushNotificationService {

    /**
     * Registers or updates an FCM device token for the given user.
     * If the token already exists (for a different user), it is reassigned.
     *
     * @param userId  the authenticated user ID
     * @param request the device token registration request
     */
    void registerDeviceToken(String userId, DeviceTokenRequestDTO request);

    /**
     * Removes a specific FCM device token.
     *
     * @param userId  the authenticated user ID (for ownership verification)
     * @param fcmToken the FCM token to unregister
     */
    void unregisterDeviceToken(String userId, String fcmToken);

    /**
     * Sends a push notification to all registered devices of the recipient.
     * If the recipient has no registered tokens, this is a no-op.
     * <p>
     * Dispatch is asynchronous and errors are logged but do not propagate.
     * Invalid/expired tokens are automatically pruned from the database.
     *
     * @param recipientId   the recipient user ID
     * @param senderName    the sender's display name
     * @param messageContent the message preview text
     * @param chatRoomId    the chat room identifier
     * @return a CompletableFuture that completes when dispatch is done
     */
    CompletableFuture<Void> sendPushNotificationToUser(
            String recipientId,
            String senderName,
            String messageContent,
            String chatRoomId);
}
