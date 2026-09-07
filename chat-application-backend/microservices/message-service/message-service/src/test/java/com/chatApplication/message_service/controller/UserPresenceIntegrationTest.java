package com.chatApplication.message_service.controller;

import com.chatApplication.message_service.dto.ChatMessageRequestDTO;
import com.chatApplication.message_service.dto.ChatMessageResponseDTO;
import com.chatApplication.message_service.entity.MessageType;
import com.chatApplication.message_service.service.InboxService;
import com.chatApplication.message_service.service.MessageService;
import com.chatApplication.message_service.service.PushNotificationService;
import com.chatApplication.message_service.service.UserPresenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.security.Principal;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Integration test verifying the presence-check + push notification pipeline
 * in ChatWebSocketController.
 * <p>
 * Tests:
 * - Push notification is triggered when recipient is OFFLINE
 * - Push notification is SKIPPED when recipient is ONLINE
 * - Push notification failure does NOT affect message delivery
 */
@ExtendWith(MockitoExtension.class)
class UserPresenceIntegrationTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private MessageService messageService;

    @Mock
    private InboxService inboxService;

    @Mock
    private UserPresenceService userPresenceService;

    @Mock
    private PushNotificationService pushNotificationService;

    @InjectMocks
    private ChatWebSocketController controller;

    private Principal principal;
    private ChatMessageRequestDTO requestDTO;
    private ChatMessageResponseDTO responseDTO;

    @BeforeEach
    void setUp() {
        principal = new UsernamePasswordAuthenticationToken(
                "user1", null,
                List.of(new SimpleGrantedAuthority("ROLE_USER")));

        requestDTO = ChatMessageRequestDTO.builder()
                .senderId("user1")
                .recipientId("user2")
                .chatRoomId("user1-user2")
                .content("Hello World")
                .messageType(MessageType.TEXT)
                .build();

        responseDTO = ChatMessageResponseDTO.builder()
                .messageId("1")
                .senderId("user1")
                .recipientId("user2")
                .chatRoomId("user1-user2")
                .content("Hello World")
                .messageType(MessageType.TEXT)
                .status("SENT")
                .timestamp(Instant.now())
                .build();
    }

    // ================================================================
    // Push notification for OFFLINE recipients
    // ================================================================

    @Nested
    @DisplayName("Recipient is OFFLINE")
    class OfflineRecipientTests {

        @Test
        @DisplayName("should trigger push notification when recipient is offline")
        void sendMessage_recipientOffline_triggersPushNotification() {
            // Arrange
            when(messageService.saveMessage(requestDTO)).thenReturn(responseDTO);
            when(userPresenceService.isUserConnected("user2")).thenReturn(false);

            // Act
            controller.sendMessage(requestDTO, principal);

            // Assert - message is persisted and broadcast
            verify(messageService).saveMessage(requestDTO);
            verify(messagingTemplate).convertAndSend(
                    eq("/topic/room.user1-user2"), eq(responseDTO));
            verify(messagingTemplate).convertAndSendToUser(
                    eq("user2"), eq("/queue/messages"), eq(responseDTO));
            verify(inboxService).notifyInboxUpdate(responseDTO);

            // Assert - push notification is triggered for offline recipient
            verify(pushNotificationService).sendPushNotificationToUser(
                    "user2", "user1", "Hello World", "user1-user2");
        }

        @Test
        @DisplayName("should still deliver message when push notification service fails")
        void sendMessage_pushNotificationFails_messageStillDelivered() {
            // Arrange
            when(messageService.saveMessage(requestDTO)).thenReturn(responseDTO);
            when(userPresenceService.isUserConnected("user2")).thenReturn(false);
            doThrow(new RuntimeException("FCM unavailable"))
                    .when(pushNotificationService)
                    .sendPushNotificationToUser(any(), any(), any(), any());

            // Act
            controller.sendMessage(requestDTO, principal);

            // Assert - message is still delivered despite push failure
            verify(messageService).saveMessage(requestDTO);
            verify(messagingTemplate).convertAndSend(
                    eq("/topic/room.user1-user2"), eq(responseDTO));
        }
    }

    // ================================================================
    // Push notification skipped for ONLINE recipients
    // ================================================================

    @Nested
    @DisplayName("Recipient is ONLINE")
    class OnlineRecipientTests {

        @Test
        @DisplayName("should skip push notification when recipient is online")
        void sendMessage_recipientOnline_skipsPushNotification() {
            // Arrange
            when(messageService.saveMessage(requestDTO)).thenReturn(responseDTO);
            when(userPresenceService.isUserConnected("user2")).thenReturn(true);

            // Act
            controller.sendMessage(requestDTO, principal);

            // Assert - message is persisted and broadcast
            verify(messageService).saveMessage(requestDTO);
            verify(messagingTemplate).convertAndSend(
                    eq("/topic/room.user1-user2"), eq(responseDTO));

            // Assert - push notification is NOT triggered for online recipient
            verify(pushNotificationService, never())
                    .sendPushNotificationToUser(any(), any(), any(), any());
        }
    }

    // ================================================================
    // Presence check failure handling
    // ================================================================

    @Nested
    @DisplayName("Presence check fails")
    class PresenceCheckFailureTests {

        @Test
        @DisplayName("should deliver message even if presence check throws exception")
        void sendMessage_presenceCheckFails_messageStillDelivered() {
            // Arrange
            when(messageService.saveMessage(requestDTO)).thenReturn(responseDTO);
            when(userPresenceService.isUserConnected("user2"))
                    .thenThrow(new RuntimeException("Redis unavailable"));

            // Act
            controller.sendMessage(requestDTO, principal);

            // Assert - message is still delivered
            verify(messageService).saveMessage(requestDTO);
            verify(messagingTemplate).convertAndSend(
                    eq("/topic/room.user1-user2"), eq(responseDTO));

            // Assert - push notification is NOT triggered (presence check failed)
            verify(pushNotificationService, never())
                    .sendPushNotificationToUser(any(), any(), any(), any());
        }
    }
}
