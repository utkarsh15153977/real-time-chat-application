package com.chatApplication.message_service.controller;

import com.chatApplication.message_service.dto.BulkStatusUpdateDTO;
import com.chatApplication.message_service.dto.MessageStatusUpdateDTO;
import com.chatApplication.message_service.entity.MessageStatus;
import com.chatApplication.message_service.service.MessageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for MessageStatusWebSocketController.
 * <p>
 * Tests the STOMP WebSocket endpoints for message status updates:
 * - /app/chat.markDelivered
 * - /app/chat.markRead
 * - /app/chat.markRoomRead
 * <p>
 * Verifies:
 * - Correct service method invocation
 * - Broadcasting to room topic and sender's status queue
 * - Principal-based recipientId extraction (security)
 * - Null safety for failed updates
 */
@ExtendWith(MockitoExtension.class)
class MessageStatusWebSocketControllerTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private MessageService messageService;

    @InjectMocks
    private MessageStatusWebSocketController controller;

    private Principal principal;
    private MessageStatusUpdateDTO deliveredUpdate;
    private MessageStatusUpdateDTO readUpdate;
    private BulkStatusUpdateDTO bulkUpdate;

    @BeforeEach
    void setUp() {
        principal = new UsernamePasswordAuthenticationToken(
                "user2",
                null,
                List.of(new SimpleGrantedAuthority("ROLE_USER")));

        deliveredUpdate = MessageStatusUpdateDTO.builder()
                .messageId("1")
                .chatRoomId("user1-user2")
                .senderId("user1")
                .recipientId("user2")
                .status(MessageStatus.DELIVERED)
                .timestamp(Instant.now())
                .build();

        readUpdate = MessageStatusUpdateDTO.builder()
                .messageId("1")
                .chatRoomId("user1-user2")
                .senderId("user1")
                .recipientId("user2")
                .status(MessageStatus.READ)
                .timestamp(Instant.now())
                .build();

        bulkUpdate = BulkStatusUpdateDTO.builder()
                .chatRoomId("user1-user2")
                .readerId("user2")
                .messageIds(List.of("1", "2", "3"))
                .status(MessageStatus.READ)
                .timestamp(Instant.now())
                .build();
    }

    // ================================================================
    // markDelivered tests
    // ================================================================

    @Nested
    @DisplayName("/app/chat.markDelivered")
    class MarkDeliveredTests {

        @Test
        @DisplayName("should call service and broadcast DELIVERED status to room and sender")
        void markDelivered_validRequest_broadcastsStatus() {
            MessageStatusWebSocketController.StatusUpdateRequest request =
                    new MessageStatusWebSocketController.StatusUpdateRequest();
            request.setMessageId("1");
            request.setChatRoomId("user1-user2");

            when(messageService.markAsDelivered("1", "user2"))
                    .thenReturn(deliveredUpdate);

            controller.markDelivered(request, principal);

            // Verify service was called with correct parameters
            verify(messageService).markAsDelivered("1", "user2");

            // Verify broadcast to room topic
            verify(messagingTemplate).convertAndSend(
                    eq("/topic/room.user1-user2"),
                    eq(deliveredUpdate));

            // Verify direct delivery to sender's status queue
            verify(messagingTemplate).convertAndSendToUser(
                    eq("user1"),
                    eq("/queue/status"),
                    eq(deliveredUpdate));
        }

        @Test
        @DisplayName("should not broadcast when service returns null (update failed)")
        void markDelivered_updateFailed_noBroadcast() {
            MessageStatusWebSocketController.StatusUpdateRequest request =
                    new MessageStatusWebSocketController.StatusUpdateRequest();
            request.setMessageId("99");
            request.setChatRoomId("user1-user2");

            when(messageService.markAsDelivered("99", "user2"))
                    .thenReturn(null);

            controller.markDelivered(request, principal);

            verify(messageService).markAsDelivered("99", "user2");
            verify(messagingTemplate, never()).convertAndSend(anyString(), any());
            verify(messagingTemplate, never()).convertAndSendToUser(anyString(), anyString(), any());
        }

        @Test
        @DisplayName("should use authenticated principal, not request payload")
        void markDelivered_usesPrincipalNotPayload() {
            MessageStatusWebSocketController.StatusUpdateRequest request =
                    new MessageStatusWebSocketController.StatusUpdateRequest();
            request.setMessageId("1");
            request.setChatRoomId("user3-user4"); // Different from principal

            // Service returns null because recipientId from principal (user2)
            // doesn't match the message's actual recipient
            when(messageService.markAsDelivered("1", "user2"))
                    .thenReturn(null);

            controller.markDelivered(request, principal);

            // Verify service was called with principal's userId, not request's
            verify(messageService).markAsDelivered("1", "user2");
        }

        @Test
        @DisplayName("should handle exception gracefully")
        void markDelivered_exceptionHandledGracefully() {
            MessageStatusWebSocketController.StatusUpdateRequest request =
                    new MessageStatusWebSocketController.StatusUpdateRequest();
            request.setMessageId("1");
            request.setChatRoomId("user1-user2");

            when(messageService.markAsDelivered("1", "user2"))
                    .thenThrow(new RuntimeException("Database error"));

            // Should not throw exception
            controller.markDelivered(request, principal);

            verify(messagingTemplate, never()).convertAndSend(anyString(), any());
        }
    }

    // ================================================================
    // markRead tests
    // ================================================================

    @Nested
    @DisplayName("/app/chat.markRead")
    class MarkReadTests {

        @Test
        @DisplayName("should call service and broadcast READ status to room and sender")
        void markRead_validRequest_broadcastsStatus() {
            MessageStatusWebSocketController.StatusUpdateRequest request =
                    new MessageStatusWebSocketController.StatusUpdateRequest();
            request.setMessageId("1");
            request.setChatRoomId("user1-user2");

            when(messageService.markAsRead("1", "user2"))
                    .thenReturn(readUpdate);

            controller.markRead(request, principal);

            // Verify service was called with correct parameters
            verify(messageService).markAsRead("1", "user2");

            // Verify broadcast to room topic
            verify(messagingTemplate).convertAndSend(
                    eq("/topic/room.user1-user2"),
                    eq(readUpdate));

            // Verify direct delivery to sender's status queue
            verify(messagingTemplate).convertAndSendToUser(
                    eq("user1"),
                    eq("/queue/status"),
                    eq(readUpdate));
        }

        @Test
        @DisplayName("should not broadcast when service returns null")
        void markRead_updateFailed_noBroadcast() {
            MessageStatusWebSocketController.StatusUpdateRequest request =
                    new MessageStatusWebSocketController.StatusUpdateRequest();
            request.setMessageId("99");
            request.setChatRoomId("user1-user2");

            when(messageService.markAsRead("99", "user2"))
                    .thenReturn(null);

            controller.markRead(request, principal);

            verify(messagingTemplate, never()).convertAndSend(anyString(), any());
        }

        @Test
        @DisplayName("should use authenticated principal for recipientId")
        void markRead_usesPrincipalNotPayload() {
            MessageStatusWebSocketController.StatusUpdateRequest request =
                    new MessageStatusWebSocketController.StatusUpdateRequest();
            request.setMessageId("1");

            when(messageService.markAsRead("1", "user2"))
                    .thenReturn(readUpdate);

            controller.markRead(request, principal);

            verify(messageService).markAsRead("1", "user2");
        }
    }

    // ================================================================
    // markRoomRead tests
    // ================================================================

    @Nested
    @DisplayName("/app/chat.markRoomRead")
    class MarkRoomReadTests {

        @Test
        @DisplayName("should call service and broadcast bulk READ status to room")
        void markRoomRead_validRequest_broadcastsBulkStatus() {
            MessageStatusWebSocketController.BulkStatusUpdateRequest request =
                    new MessageStatusWebSocketController.BulkStatusUpdateRequest();
            request.setChatRoomId("user1-user2");

            when(messageService.markChatRoomAsRead("user1-user2", "user2"))
                    .thenReturn(bulkUpdate);

            controller.markRoomRead(request, principal);

            // Verify service was called with correct parameters
            verify(messageService).markChatRoomAsRead("user1-user2", "user2");

            // Verify broadcast to room topic
            verify(messagingTemplate).convertAndSend(
                    eq("/topic/room.user1-user2"),
                    eq(bulkUpdate));
        }

        @Test
        @DisplayName("should not broadcast when no messages were updated")
        void markRoomRead_noUpdates_noBroadcast() {
            MessageStatusWebSocketController.BulkStatusUpdateRequest request =
                    new MessageStatusWebSocketController.BulkStatusUpdateRequest();
            request.setChatRoomId("user1-user2");

            BulkStatusUpdateDTO emptyUpdate = BulkStatusUpdateDTO.builder()
                    .chatRoomId("user1-user2")
                    .readerId("user2")
                    .messageIds(List.of())
                    .status(MessageStatus.READ)
                    .timestamp(Instant.now())
                    .build();

            when(messageService.markChatRoomAsRead("user1-user2", "user2"))
                    .thenReturn(emptyUpdate);

            controller.markRoomRead(request, principal);

            // Verify no broadcast (empty message list)
            verify(messagingTemplate, never()).convertAndSend(anyString(), any());
        }

        @Test
        @DisplayName("should use authenticated principal for readerId")
        void markRoomRead_usesPrincipalNotPayload() {
            MessageStatusWebSocketController.BulkStatusUpdateRequest request =
                    new MessageStatusWebSocketController.BulkStatusUpdateRequest();
            request.setChatRoomId("user3-user4"); // Different from principal

            when(messageService.markChatRoomAsRead("user3-user4", "user2"))
                    .thenReturn(bulkUpdate);

            controller.markRoomRead(request, principal);

            // Verify service was called with principal's userId
            verify(messageService).markChatRoomAsRead("user3-user4", "user2");
        }

        @Test
        @DisplayName("should handle exception gracefully")
        void markRoomRead_exceptionHandledGracefully() {
            MessageStatusWebSocketController.BulkStatusUpdateRequest request =
                    new MessageStatusWebSocketController.BulkStatusUpdateRequest();
            request.setChatRoomId("user1-user2");

            when(messageService.markChatRoomAsRead("user1-user2", "user2"))
                    .thenThrow(new RuntimeException("Database error"));

            // Should not throw exception
            controller.markRoomRead(request, principal);

            verify(messagingTemplate, never()).convertAndSend(anyString(), any());
        }
    }

    // ================================================================
    // Security tests
    // ================================================================

    @Nested
    @DisplayName("Security")
    class SecurityTests {

        @Test
        @DisplayName("should reject when principal is null")
        void allEndpoints_nullPrincipal_noAction() {
            MessageStatusWebSocketController.StatusUpdateRequest request =
                    new MessageStatusWebSocketController.StatusUpdateRequest();
            request.setMessageId("1");
            request.setChatRoomId("user1-user2");

            controller.markDelivered(request, null);
            controller.markRead(request, null);

            verify(messageService, never()).markAsDelivered(anyString(), anyString());
            verify(messageService, never()).markAsRead(anyString(), anyString());
            verify(messagingTemplate, never()).convertAndSend(anyString(), any());
        }

        @Test
        @DisplayName("should extract userId from Authentication principal")
        void allEndpoints_extractsUserIdFromAuthentication() {
            Principal authPrincipal = new UsernamePasswordAuthenticationToken(
                    "authenticatedUser",
                    null,
                    List.of(new SimpleGrantedAuthority("ROLE_USER")));

            MessageStatusWebSocketController.StatusUpdateRequest request =
                    new MessageStatusWebSocketController.StatusUpdateRequest();
            request.setMessageId("1");
            request.setChatRoomId("user1-user2");

            when(messageService.markAsDelivered("1", "authenticatedUser"))
                    .thenReturn(deliveredUpdate);

            controller.markDelivered(request, authPrincipal);

            verify(messageService).markAsDelivered("1", "authenticatedUser");
        }
    }
}
