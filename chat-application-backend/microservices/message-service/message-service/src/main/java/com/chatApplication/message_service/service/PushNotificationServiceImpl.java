package com.chatApplication.message_service.service;

import com.chatApplication.message_service.config.FCMConfig;
import com.chatApplication.message_service.dto.DeviceTokenRequestDTO;
import com.chatApplication.message_service.entity.DeviceType;
import com.chatApplication.message_service.entity.UserDeviceToken;
import com.chatApplication.message_service.repository.UserDeviceTokenRepository;
import com.google.firebase.messaging.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Implementation of push notification service using Firebase Cloud Messaging.
 * <p>
 * Handles device token lifecycle (register/unregister) and asynchronous
 * FCM multicast dispatch with automatic invalid token cleanup.
 * <p>
 * FCM errors (Unregistered, InvalidArgument) trigger automatic token pruning
 * to keep the device token registry clean.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PushNotificationServiceImpl implements PushNotificationService {

    private final UserDeviceTokenRepository deviceTokenRepository;

    private static final String TITLE_NEW_MESSAGE = "New Message";

    @Override
    @Transactional
    public void registerDeviceToken(String userId, DeviceTokenRequestDTO request) {
        String fcmToken = request.getFcmToken();
        DeviceType deviceType = request.getDeviceType();

        log.info("Registering device token: userId={}, deviceType={}, tokenPreview={}",
                userId, deviceType, maskToken(fcmToken));

        // Check if token already exists (possibly for a different user - reassignment)
        Optional<UserDeviceToken> existingToken = deviceTokenRepository.findByFcmToken(fcmToken);

        if (existingToken.isPresent()) {
            UserDeviceToken existing = existingToken.get();
            if (existing.getUserId().equals(userId)) {
                // Same user, same token - update device type if changed
                if (existing.getDeviceType() != deviceType) {
                    existing.setDeviceType(deviceType);
                    deviceTokenRepository.save(existing);
                    log.debug("Updated device type for existing token: userId={}, newType={}",
                            userId, deviceType);
                }
                return;
            }
            // Token belongs to different user - reassign
            log.info("Reassigning token from userId={} to userId={}",
                    existing.getUserId(), userId);
            existing.setUserId(userId);
            existing.setDeviceType(deviceType);
            deviceTokenRepository.save(existing);
            return;
        }

        // New token - persist
        UserDeviceToken token = UserDeviceToken.builder()
                .userId(userId)
                .fcmToken(fcmToken)
                .deviceType(deviceType)
                .build();

        deviceTokenRepository.save(token);
        log.info("Device token registered: userId={}, deviceType={}", userId, deviceType);
    }

    @Override
    @Transactional
    public void unregisterDeviceToken(String userId, String fcmToken) {
        log.info("Unregistering device token: userId={}, tokenPreview={}",
                userId, maskToken(fcmToken));

        Optional<UserDeviceToken> existingToken = deviceTokenRepository.findByFcmToken(fcmToken);

        if (existingToken.isEmpty()) {
            log.debug("Token not found, nothing to unregister: tokenPreview={}", maskToken(fcmToken));
            return;
        }

        UserDeviceToken token = existingToken.get();
        if (!token.getUserId().equals(userId)) {
            log.warn("Token ownership mismatch: token belongs to userId={}, requested by userId={}",
                    token.getUserId(), userId);
            return;
        }

        deviceTokenRepository.deleteByFcmToken(fcmToken);
        log.info("Device token unregistered: userId={}, deviceType={}",
                userId, token.getDeviceType());
    }

    @Override
    @Transactional(readOnly = true)
    public CompletableFuture<Void> sendPushNotificationToUser(
            String recipientId,
            String senderName,
            String messageContent,
            String chatRoomId) {

        if (!FCMConfig.isFirebaseEnabled()) {
            log.debug("Firebase not enabled, skipping push notification for user {}", recipientId);
            return CompletableFuture.completedFuture(null);
        }

        List<UserDeviceToken> tokens = deviceTokenRepository.findByUserId(recipientId);
        if (tokens.isEmpty()) {
            log.debug("No registered devices for user {}, skipping push", recipientId);
            return CompletableFuture.completedFuture(null);
        }

        log.info("Sending push notification to user {} on {} device(s)",
                recipientId, tokens.size());

        // Build notification data payload
        String title = senderName != null ? senderName : TITLE_NEW_MESSAGE;
        String body = messageContent != null ? truncate(messageContent, 256) : "You have a new message";

        List<String> fcmTokens = tokens.stream()
                .map(UserDeviceToken::getFcmToken)
                .toList();

        // Build MulticastMessage using Map for data payload
        java.util.Map<String, String> dataMap = new java.util.HashMap<>();
        dataMap.put("type", "CHAT_MESSAGE");
        dataMap.put("senderName", title);
        dataMap.put("messageContent", body);
        dataMap.put("chatRoomId", chatRoomId);
        dataMap.put("recipientId", recipientId);

        Notification notification = Notification.builder()
                .setTitle(title)
                .setBody(body)
                .build();

        AndroidConfig androidConfig = AndroidConfig.builder()
                .setPriority(AndroidConfig.Priority.HIGH)
                .setNotification(AndroidNotification.builder()
                        .setSound("default")
                        .setChannelId("chat_messages")
                        .build())
                .build();

        ApnsConfig apnsConfig = ApnsConfig.builder()
                .setAps(Aps.builder()
                        .setSound("default")
                        .setBadge(1)
                        .build())
                .build();

        MulticastMessage multicastMessage = MulticastMessage.builder()
                .addAllTokens(fcmTokens)
                .putAllData(dataMap)
                .setNotification(notification)
                .setAndroidConfig(androidConfig)
                .setApnsConfig(apnsConfig)
                .build();

        try {
            BatchResponse response = FirebaseMessaging.getInstance()
                    .sendEachForMulticast(multicastMessage);

            log.info("FCM multicast result: success={}, failure={}",
                    response.getSuccessCount(), response.getFailureCount());

            // Prune invalid tokens
            handleFailedTokens(response, fcmTokens, tokens);

        } catch (FirebaseMessagingException e) {
            log.error("FCM multicast failed for user {}: {}", recipientId, e.getMessage());
        }

        return CompletableFuture.completedFuture(null);
    }

    /**
     * Handles failed FCM responses by pruning invalid/expired tokens from the database.
     */
    private void handleFailedTokens(
            BatchResponse response,
            List<String> allTokens,
            List<UserDeviceToken> tokenEntities) {

        List<String> tokensToRemove = new ArrayList<>();

        for (int i = 0; i < response.getResponses().size(); i++) {
            SendResponse sendResponse = response.getResponses().get(i);
            if (!sendResponse.isSuccessful()) {
                FirebaseMessagingException exception = sendResponse.getException();
                if (exception != null) {
                    String error = exception.getMessagingErrorCode() != null
                            ? exception.getMessagingErrorCode().name()
                            : "UNKNOWN";

                    if ("UNREGISTERED".equals(error) || "INVALID_ARGUMENT".equals(error)) {
                        tokensToRemove.add(allTokens.get(i));
                        log.debug("Token marked for pruning: tokenPreview={}, error={}",
                                maskToken(allTokens.get(i)), error);
                    }
                }
            }
        }

        if (!tokensToRemove.isEmpty()) {
            log.info("Pruning {} invalid/expired FCM tokens", tokensToRemove.size());
            for (String token : tokensToRemove) {
                try {
                    deviceTokenRepository.deleteByFcmToken(token);
                } catch (Exception e) {
                    log.warn("Failed to prune token {}: {}", maskToken(token), e.getMessage());
                }
            }
        }
    }

    /**
     * Returns the count of registered device tokens for a user.
     * Useful for testing and monitoring.
     */
    @Transactional(readOnly = true)
    public int getDeviceCount(String userId) {
        return deviceTokenRepository.findByUserId(userId).size();
    }

    /**
     * Returns all registered device tokens for a user.
     * Useful for testing.
     */
    @Transactional(readOnly = true)
    public List<UserDeviceToken> getDevicesByUserId(String userId) {
        return deviceTokenRepository.findByUserId(userId);
    }

    private String maskToken(String token) {
        if (token == null || token.length() < 8) return "****";
        return token.substring(0, 4) + "****" + token.substring(token.length() - 4);
    }

    private String truncate(String text, int maxLength) {
        if (text == null) return "";
        return text.length() > maxLength ? text.substring(0, maxLength) + "..." : text;
    }
}
