package com.chatApplication.message_service.controller;

import com.chatApplication.message_service.dto.ChatMessageRequestDTO;
import com.chatApplication.message_service.dto.ChatMessageResponseDTO;
import com.chatApplication.message_service.dto.TypingEvent;
import com.chatApplication.message_service.exception.MessageValidationException;
import com.chatApplication.message_service.service.MessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;

import java.security.Principal;

/**
 * STOMP WebSocket controller for real-time chat messaging.
 * <p>
 * Security model:
 *   - senderId is ALWAYS extracted from the authenticated Principal
 *   - Never trusts the client-provided senderId field (spoofing protection)
 *   - Principal is set by WebSocketAuthInterceptor during CONNECT frame
 * <p>
 * Endpoints:
 * - /app/chat.sendMessage: Send a text or media message
 * - /app/chat.typing: Send a typing indicator (transient, not persisted)
 * <p>
 * Broadcast destinations:
 * - /topic/room.{chatRoomId}: All subscribers in the chat room
 * - /user/{userId}/queue/messages: Direct message to specific user
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class ChatWebSocketController {

    private final SimpMessagingTemplate messagingTemplate;
    private final MessageService messageService;

    /**
     * Handles incoming chat messages via STOMP.
     * <p>
     * Security: senderId is extracted from the authenticated Principal,
     * NOT from the request DTO. This prevents message spoofing.
     *
     * @param requestDTO the incoming message payload (senderId field is ignored)
     * @param principal  the authenticated user (set by WebSocketAuthInterceptor)
     */
    @MessageMapping("/chat.sendMessage")
    public void sendMessage(ChatMessageRequestDTO requestDTO, Principal principal) {
        // Extract authenticated senderId from Principal (never trust client payload)
        String senderId = extractSenderId(principal);
        if (senderId == null) {
            log.warn("sendMessage rejected: no authenticated principal");
            return;
        }

        // Override client-provided senderId with authenticated value
        requestDTO.setSenderId(senderId);

        log.info("WebSocket message: sender={}, type={}, room={}",
                senderId,
                requestDTO.getMessageType(),
                requestDTO.getChatRoomId());

        try {
            // 1. Persist the message to PostgreSQL
            ChatMessageResponseDTO responseDTO =
                    messageService.saveMessage(requestDTO);

            // 2. Broadcast to all subscribers in the chat room
            String roomTopic = "/topic/room." + requestDTO.getChatRoomId();
            messagingTemplate.convertAndSend(roomTopic, responseDTO);

            // 3. Also send to recipient's personal queue for direct delivery
            messagingTemplate.convertAndSendToUser(
                    requestDTO.getRecipientId(),
                    "/queue/messages",
                    responseDTO);

            log.info("Message broadcast: id={}, room={}",
                    responseDTO.getMessageId(),
                    requestDTO.getChatRoomId());

        } catch (MessageValidationException e) {
            log.warn("Message validation failed: {}", e.getMessage());
            messagingTemplate.convertAndSendToUser(
                    senderId,
                    "/queue/errors",
                    e.getMessage());
        } catch (Exception e) {
            log.error("Failed to process WebSocket message", e);
            messagingTemplate.convertAndSendToUser(
                    senderId,
                    "/queue/errors",
                    "Failed to send message. Please try again.");
        }
    }

    /**
     * Handles typing indicators (transient, not persisted).
     * <p>
     * Security: senderId is extracted from the authenticated Principal.
     *
     * @param event     the typing event payload (senderId field is ignored)
     * @param principal the authenticated user
     */
    @MessageMapping("/chat.typing")
    public void typing(TypingEvent event, Principal principal) {
        String senderId = extractSenderId(principal);
        if (senderId == null) {
            return;
        }

        // Override with authenticated senderId
        event.setSenderId(senderId);

        messagingTemplate.convertAndSendToUser(
                event.getReceiverId(),
                "/queue/typing",
                event);
    }

    /**
     * Extracts the sender ID from the authenticated Principal.
     * The Principal name is set to userId by WebSocketAuthInterceptor.
     *
     * @param principal the STOMP principal
     * @return the authenticated user ID, or null if not authenticated
     */
    private String extractSenderId(Principal principal) {
        if (principal == null) {
            return null;
        }

        // If principal is a Spring Security Authentication, get the name
        if (principal instanceof Authentication auth) {
            return auth.getName();
        }

        return principal.getName();
    }
}
