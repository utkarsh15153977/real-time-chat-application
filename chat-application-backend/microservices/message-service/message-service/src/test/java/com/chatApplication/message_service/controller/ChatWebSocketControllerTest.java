package com.chatApplication.message_service.controller;

import com.chatApplication.message_service.dto.ChatMessageRequestDTO;
import com.chatApplication.message_service.dto.ChatMessageResponseDTO;
import com.chatApplication.message_service.entity.MessageType;
import com.chatApplication.message_service.service.MessageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ChatWebSocketController.
 * <p>
 * Tests the WebSocket message handling flow:
 * - Message persistence via MessageService
 * - Broadcast to chat room topic via SimpMessagingTemplate
 * - Error handling for validation failures
 */
@ExtendWith(MockitoExtension.class)
class ChatWebSocketControllerTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private MessageService messageService;

    @InjectMocks
    private ChatWebSocketController controller;

    private ChatMessageRequestDTO textRequest;
    private ChatMessageRequestDTO imageRequest;
    private ChatMessageResponseDTO textResponse;
    private ChatMessageResponseDTO imageResponse;

    @BeforeEach
    void setUp() {
        textRequest = ChatMessageRequestDTO.builder()
                .senderId("user1")
                .recipientId("user2")
                .chatRoomId("user1-user2")
                .content("Hello World")
                .messageType(MessageType.TEXT)
                .build();

        imageRequest = ChatMessageRequestDTO.builder()
                .senderId("user1")
                .recipientId("user2")
                .chatRoomId("user1-user2")
                .messageType(MessageType.IMAGE)
                .mediaUrl("https://s3.amazonaws.com/bucket/chats/room/photo.jpg")
                .fileKey("chats/room/uuid-photo.jpg")
                .fileSizeBytes(2048000L)
                .build();

        textResponse = ChatMessageResponseDTO.builder()
                .messageId("1")
                .senderId("user1")
                .recipientId("user2")
                .chatRoomId("user1-user2")
                .content("Hello World")
                .messageType(MessageType.TEXT)
                .status("SENT")
                .timestamp(Instant.now())
                .build();

        imageResponse = ChatMessageResponseDTO.builder()
                .messageId("2")
                .senderId("user1")
                .recipientId("user2")
                .chatRoomId("user1-user2")
                .messageType(MessageType.IMAGE)
                .mediaUrl("https://s3.amazonaws.com/bucket/chats/room/photo.jpg")
                .fileKey("chats/room/uuid-photo.jpg")
                .fileSizeBytes(2048000L)
                .status("SENT")
                .timestamp(Instant.now())
                .build();
    }

    @Test
    @DisplayName("should persist and broadcast TEXT message to chat room topic")
    void sendMessage_textMessage_persistsAndBroadcasts() {
        when(messageService.saveMessage(textRequest)).thenReturn(textResponse);

        controller.sendMessage(textRequest);

        // Verify message was persisted
        verify(messageService).saveMessage(textRequest);

        // Verify broadcast to chat room topic
        verify(messagingTemplate).convertAndSend(
                eq("/topic/room.user1-user2"),
                eq(textResponse));

        // Verify direct delivery to recipient
        verify(messagingTemplate).convertAndSendToUser(
                eq("user2"),
                eq("/queue/messages"),
                eq(textResponse));
    }

    @Test
    @DisplayName("should persist and broadcast IMAGE message with media fields")
    void sendMessage_imageMessage_persistsAndBroadcasts() {
        when(messageService.saveMessage(imageRequest)).thenReturn(imageResponse);

        controller.sendMessage(imageRequest);

        // Verify message was persisted
        verify(messageService).saveMessage(imageRequest);

        // Verify broadcast to chat room topic
        verify(messagingTemplate).convertAndSend(
                eq("/topic/room.user1-user2"),
                eq(imageResponse));

        // Verify direct delivery to recipient
        verify(messagingTemplate).convertAndSendToUser(
                eq("user2"),
                eq("/queue/messages"),
                eq(imageResponse));
    }

    @Test
    @DisplayName("should send error to sender when validation fails")
    void sendMessage_validationFailed_sendsErrorToSender() {
        when(messageService.saveMessage(any()))
                .thenThrow(new com.chatApplication.message_service.exception
                        .MessageValidationException("mediaUrl is required for IMAGE messages"));

        controller.sendMessage(imageRequest);

        // Verify error was sent to sender's error queue
        verify(messagingTemplate).convertAndSendToUser(
                eq("user1"),
                eq("/queue/errors"),
                contains("mediaUrl is required"));

        // Verify no broadcast to room (message was not persisted)
        verify(messagingTemplate, never()).convertAndSend(
                anyString(), any());
    }

    @Test
    @DisplayName("should send generic error when unexpected exception occurs")
    void sendMessage_unexpectedException_sendsGenericError() {
        when(messageService.saveMessage(any()))
                .thenThrow(new RuntimeException("Database connection lost"));

        controller.sendMessage(textRequest);

        // Verify generic error was sent to sender
        verify(messagingTemplate).convertAndSendToUser(
                eq("user1"),
                eq("/queue/errors"),
                contains("Failed to send message"));

        // Verify no broadcast to room
        verify(messagingTemplate, never()).convertAndSend(
                anyString(), any());
    }

    @Test
    @DisplayName("should broadcast typing indicator to recipient")
    void typing_sendsToRecipient() {
        com.chatApplication.message_service.dto.TypingEvent event =
                com.chatApplication.message_service.dto.TypingEvent.builder()
                        .senderId("user1")
                        .receiverId("user2")
                        .typing(true)
                        .build();

        controller.typing(event);

        verify(messagingTemplate).convertAndSendToUser(
                eq("user2"),
                eq("/queue/typing"),
                eq(event));
    }
}
