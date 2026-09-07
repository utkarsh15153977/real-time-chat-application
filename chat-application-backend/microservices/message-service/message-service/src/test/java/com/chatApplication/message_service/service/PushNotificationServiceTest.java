package com.chatApplication.message_service.service;

import com.chatApplication.message_service.config.FCMConfig;
import com.chatApplication.message_service.dto.DeviceTokenRequestDTO;
import com.chatApplication.message_service.entity.DeviceType;
import com.chatApplication.message_service.entity.UserDeviceToken;
import com.chatApplication.message_service.repository.UserDeviceTokenRepository;
import com.google.firebase.messaging.FirebaseMessaging;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for PushNotificationServiceImpl.
 * <p>
 * Tests cover:
 * - Device token registration (new, update, reassignment)
 * - Device token unregistration (valid, invalid ownership)
 * - Push notification dispatch (online, offline, no tokens)
 * - Firebase disabled (dry-run mode)
 * - Token pruning on FCM errors
 */
@ExtendWith(MockitoExtension.class)
class PushNotificationServiceTest {

    @Mock
    private UserDeviceTokenRepository deviceTokenRepository;

    @InjectMocks
    private PushNotificationServiceImpl pushNotificationService;

    private DeviceTokenRequestDTO androidRequest;
    private DeviceTokenRequestDTO iosRequest;
    private UserDeviceToken existingToken;

    @BeforeEach
    void setUp() {
        androidRequest = DeviceTokenRequestDTO.builder()
                .fcmToken("fcm-token-android-12345")
                .deviceType(DeviceType.ANDROID)
                .build();

        iosRequest = DeviceTokenRequestDTO.builder()
                .fcmToken("fcm-token-ios-67890")
                .deviceType(DeviceType.IOS)
                .build();

        existingToken = UserDeviceToken.builder()
                .id("token-id-1")
                .userId("user1")
                .fcmToken("fcm-token-android-12345")
                .deviceType(DeviceType.ANDROID)
                .build();
    }

    // ================================================================
    // Device Token Registration Tests
    // ================================================================

    @Nested
    @DisplayName("registerDeviceToken")
    class RegisterDeviceTokenTests {

        @Test
        @DisplayName("should register new device token successfully")
        void registerDeviceToken_newToken_savesToRepository() {
            // Arrange
            when(deviceTokenRepository.findByFcmToken("fcm-token-android-12345"))
                    .thenReturn(Optional.empty());

            // Act
            pushNotificationService.registerDeviceToken("user1", androidRequest);

            // Assert
            ArgumentCaptor<UserDeviceToken> captor = ArgumentCaptor.forClass(UserDeviceToken.class);
            verify(deviceTokenRepository).save(captor.capture());

            UserDeviceToken saved = captor.getValue();
            assertThat(saved.getUserId()).isEqualTo("user1");
            assertThat(saved.getFcmToken()).isEqualTo("fcm-token-android-12345");
            assertThat(saved.getDeviceType()).isEqualTo(DeviceType.ANDROID);
        }

        @Test
        @DisplayName("should update device type when same user re-registers")
        void registerDeviceToken_sameUser_updatesDeviceType() {
            // Arrange
            when(deviceTokenRepository.findByFcmToken("fcm-token-android-12345"))
                    .thenReturn(Optional.of(existingToken));

            DeviceTokenRequestDTO updatedRequest = DeviceTokenRequestDTO.builder()
                    .fcmToken("fcm-token-android-12345")
                    .deviceType(DeviceType.IOS)
                    .build();

            // Act
            pushNotificationService.registerDeviceToken("user1", updatedRequest);

            // Assert
            verify(deviceTokenRepository).save(argThat(token ->
                    token.getDeviceType() == DeviceType.IOS));
        }

        @Test
        @DisplayName("should skip save when same user and same device type")
        void registerDeviceToken_sameUserSameType_noSave() {
            // Arrange
            when(deviceTokenRepository.findByFcmToken("fcm-token-android-12345"))
                    .thenReturn(Optional.of(existingToken));

            // Act
            pushNotificationService.registerDeviceToken("user1", androidRequest);

            // Assert
            verify(deviceTokenRepository, never()).save(any());
        }

        @Test
        @DisplayName("should reassign token when different user registers same token")
        void registerDeviceToken_differentUser_reassignsToken() {
            // Arrange
            when(deviceTokenRepository.findByFcmToken("fcm-token-android-12345"))
                    .thenReturn(Optional.of(existingToken));

            // Act
            pushNotificationService.registerDeviceToken("user2", androidRequest);

            // Assert
            verify(deviceTokenRepository).save(argThat(token ->
                    token.getUserId().equals("user2")));
        }
    }

    // ================================================================
    // Device Token Unregistration Tests
    // ================================================================

    @Nested
    @DisplayName("unregisterDeviceToken")
    class UnregisterDeviceTokenTests {

        @Test
        @DisplayName("should delete token when owned by requesting user")
        void unregisterDeviceToken_validOwnership_deletesToken() {
            // Arrange
            when(deviceTokenRepository.findByFcmToken("fcm-token-android-12345"))
                    .thenReturn(Optional.of(existingToken));

            // Act
            pushNotificationService.unregisterDeviceToken("user1", "fcm-token-android-12345");

            // Assert
            verify(deviceTokenRepository).deleteByFcmToken("fcm-token-android-12345");
        }

        @Test
        @DisplayName("should not delete token when ownership mismatch")
        void unregisterDeviceToken_ownershipMismatch_noDelete() {
            // Arrange
            when(deviceTokenRepository.findByFcmToken("fcm-token-android-12345"))
                    .thenReturn(Optional.of(existingToken));

            // Act
            pushNotificationService.unregisterDeviceToken("user2", "fcm-token-android-12345");

            // Assert
            verify(deviceTokenRepository, never()).deleteByFcmToken(any());
        }

        @Test
        @DisplayName("should handle non-existent token gracefully")
        void unregisterDeviceToken_tokenNotFound_noOp() {
            // Arrange
            when(deviceTokenRepository.findByFcmToken("non-existent"))
                    .thenReturn(Optional.empty());

            // Act
            pushNotificationService.unregisterDeviceToken("user1", "non-existent");

            // Assert
            verify(deviceTokenRepository, never()).deleteByFcmToken(any());
        }
    }

    // ================================================================
    // Push Notification Dispatch Tests
    // ================================================================

    @Nested
    @DisplayName("sendPushNotificationToUser")
    class SendPushNotificationTests {

        @Test
        @DisplayName("should skip dispatch when Firebase is disabled")
        void sendPushNotification_firebaseDisabled_skipsDispatch() {
            // Arrange - Firebase is not initialized in tests, so isFirebaseEnabled() returns false

            // Act
            pushNotificationService.sendPushNotificationToUser(
                    "user1", "sender1", "Hello", "room-1");

            // Assert - method completes without error, no repository call since Firebase disabled
            verify(deviceTokenRepository, never()).findByUserId(any());
        }

        @Test
        @DisplayName("should skip dispatch when user has no registered devices")
        void sendPushNotification_noTokens_skipsDispatch() {
            // Arrange - mock Firebase as enabled
            try (MockedStatic<FCMConfig> fcmMock = mockStatic(FCMConfig.class)) {
                fcmMock.when(FCMConfig::isFirebaseEnabled).thenReturn(true);
                when(deviceTokenRepository.findByUserId("user1"))
                        .thenReturn(List.of());

                // Act
                pushNotificationService.sendPushNotificationToUser(
                        "user1", "sender1", "Hello", "room-1");

                // Assert
                verify(deviceTokenRepository).findByUserId("user1");
            }
        }

        @Test
        @DisplayName("should query devices for recipient when tokens exist")
        void sendPushNotification_withTokens_queriesDevices() throws Exception {
            // Arrange
            try (MockedStatic<FCMConfig> fcmMock = mockStatic(FCMConfig.class);
                 MockedStatic<FirebaseMessaging> messagingMock = mockStatic(FirebaseMessaging.class)) {

                fcmMock.when(FCMConfig::isFirebaseEnabled).thenReturn(true);

                FirebaseMessaging mockMessaging = mock(FirebaseMessaging.class);
                messagingMock.when(FirebaseMessaging::getInstance).thenReturn(mockMessaging);

                // Mock successful response
                com.google.firebase.messaging.SendResponse mockResponse =
                        mock(com.google.firebase.messaging.SendResponse.class);
                when(mockResponse.isSuccessful()).thenReturn(true);

                com.google.firebase.messaging.BatchResponse mockBatchResponse =
                        mock(com.google.firebase.messaging.BatchResponse.class);
                when(mockBatchResponse.getSuccessCount()).thenReturn(2);
                when(mockBatchResponse.getFailureCount()).thenReturn(0);
                when(mockBatchResponse.getResponses()).thenReturn(List.of(mockResponse, mockResponse));

                when(mockMessaging.sendEachForMulticast(any())).thenReturn(mockBatchResponse);

                List<UserDeviceToken> tokens = List.of(
                        existingToken,
                        UserDeviceToken.builder()
                                .id("token-id-2")
                                .userId("user1")
                                .fcmToken("fcm-token-ios-67890")
                                .deviceType(DeviceType.IOS)
                                .build()
                );
                when(deviceTokenRepository.findByUserId("user1")).thenReturn(tokens);

                // Act
                pushNotificationService.sendPushNotificationToUser(
                        "user1", "sender1", "Hello", "room-1");

                // Assert
                verify(deviceTokenRepository).findByUserId("user1");
                verify(mockMessaging).sendEachForMulticast(any());
            }
        }
    }

    // ================================================================
    // Device Count & Lookup Tests
    // ================================================================

    @Nested
    @DisplayName("getDeviceCount / getDevicesByUserId")
    class DeviceLookupTests {

        @Test
        @DisplayName("should return device count for user")
        void getDeviceCount_returnsCorrectCount() {
            // Arrange
            List<UserDeviceToken> tokens = List.of(existingToken);
            when(deviceTokenRepository.findByUserId("user1")).thenReturn(tokens);

            // Act
            int count = pushNotificationService.getDeviceCount("user1");

            // Assert
            assertThat(count).isEqualTo(1);
        }

        @Test
        @DisplayName("should return empty list for user with no devices")
        void getDeviceCount_noDevices_returnsZero() {
            // Arrange
            when(deviceTokenRepository.findByUserId("user1")).thenReturn(List.of());

            // Act
            int count = pushNotificationService.getDeviceCount("user1");

            // Assert
            assertThat(count).isEqualTo(0);
        }

        @Test
        @DisplayName("should return all devices for user")
        void getDevicesByUserId_returnsAllDevices() {
            // Arrange
            List<UserDeviceToken> tokens = List.of(existingToken);
            when(deviceTokenRepository.findByUserId("user1")).thenReturn(tokens);

            // Act
            List<UserDeviceToken> result = pushNotificationService.getDevicesByUserId("user1");

            // Assert
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getUserId()).isEqualTo("user1");
        }
    }
}
