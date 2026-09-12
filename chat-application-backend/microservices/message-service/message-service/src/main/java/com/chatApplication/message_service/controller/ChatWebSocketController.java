package com.chatApplication.message_service.controller;

import com.chatApplication.message_service.dto.ChatMessageRequestDTO;
import com.chatApplication.message_service.dto.ChatMessageResponseDTO;
import com.chatApplication.message_service.dto.TypingEvent;
import com.chatApplication.message_service.entity.MessageType;
import com.chatApplication.message_service.exception.MessageValidationException;
import com.chatApplication.message_service.kafka.MessageCreatedEvent;
import com.chatApplication.message_service.kafka.MessageEventPublisher;
import com.chatApplication.message_service.service.InboxService;
import com.chatApplication.message_service.service.MessageService;
import com.chatApplication.message_service.service.PushNotificationService;
import com.chatApplication.message_service.service.UserPresenceService;
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
 * Message Pipeline:
 *   1. Persist message to PostgreSQL
 *   2. Broadcast via STOMP (real-time delivery)
 *   3. Send inbox update
 *   4. Publish Kafka event (async downstream consumers)
 *   5. Push notification for offline recipients
 * <p>
 * Broadcast destinations:
 * - /topic/room.{chatRoomId}: All subscribers in the chat room
 * - /user/{userId}/queue/messages: Direct message to specific user
 * - /user/{userId}/queue/inbox: Real-time inbox update (Phase 4)
 * - chat.message-created topic: Kafka event for downstream services (Phase 6)
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class ChatWebSocketController {

    private final SimpMessagingTemplate messagingTemplate;
    private final MessageService messageService;
    private final InboxService inboxService;
    private final UserPresenceService userPresenceService;
    private final PushNotificationService pushNotificationService;
    private final MessageEventPublisher messageEventPublisher;

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
        String senderId = extractSenderId(principal);
        if (senderId == null) {
            log.warn("sendMessage rejected: no authenticated principal");
            return;
        }

        requestDTO.setSenderId(senderId);

        log.info("WebSocket message: sender={}, type={}, room={}, loadTestId={}",
                senderId,
                requestDTO.getMessageType(),
                requestDTO.getChatRoomId(),
                requestDTO.getLoadTestId());

        try {
            // 1. Persist the message to PostgreSQL
            ChatMessageResponseDTO responseDTO =
                    messageService.saveMessage(requestDTO);

            // 2. Broadcast to all subscribers in the chat room
            String roomTopic = "/topic/room." + requestDTO.getChatRoomId();
            messagingTemplate.convertAndSend(roomTopic, responseDTO);

            // 2b. Also broadcast to /topic/public for load-testing subscribers
            messagingTemplate.convertAndSend("/topic/public", responseDTO);

            // 3. Send to recipient's personal queue for direct delivery
            messagingTemplate.convertAndSendToUser(
                    requestDTO.getRecipientId(),
                    "/queue/messages",
                    responseDTO);

            // 4. Send real-time inbox update (Phase 4)
            inboxService.notifyInboxUpdate(responseDTO);

            // 5. Publish Kafka event for downstream consumers (Phase 6)
            publishMessageCreatedEvent(responseDTO, requestDTO);

            // 6. Push notification for offline recipients (Phase 5)
            notifyOfflineRecipient(
                    requestDTO.getRecipientId(),
                    senderId,
                    requestDTO.getContent(),
                    requestDTO.getChatRoomId());

            log.info("Message broadcast: id={}, room={}, responseLoadTestId={}",
                    responseDTO.getMessageId(),
                    requestDTO.getChatRoomId(),
                    responseDTO.getLoadTestId());

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
     */
    @MessageMapping("/chat.typing")
    public void typing(TypingEvent event, Principal principal) {
        String senderId = extractSenderId(principal);
        if (senderId == null) {
            return;
        }

        event.setSenderId(senderId);

        messagingTemplate.convertAndSendToUser(
                event.getReceiverId(),
                "/queue/typing",
                event);
    }

    /**
     * Builds and publishes a {@link MessageCreatedEvent} to Kafka.
     * <p>
     * Publication failures are fire-and-forget: the message has already
     * been persisted and broadcast via STOMP, so Kafka failures only
     * affect downstream consumers, not the core message delivery.
     *
     * @param responseDTO the persisted message response
     * @param requestDTO  the original request (for chatRoomId)
     */
    private void publishMessageCreatedEvent(
            ChatMessageResponseDTO responseDTO,
            ChatMessageRequestDTO requestDTO) {

        try {
            MessageCreatedEvent event = MessageCreatedEvent.builder()
                    .messageId(responseDTO.getMessageId())
                    .chatRoomId(requestDTO.getChatRoomId())
                    .senderId(responseDTO.getSenderId())
                    .recipientId(responseDTO.getRecipientId())
                    .content(responseDTO.getContent())
                    .type(responseDTO.getMessageType())
                    .createdAt(responseDTO.getTimestamp())
                    .build();

            messageEventPublisher.publishMessageCreatedEvent(event);

        } catch (Exception e) {
            // Kafka publication failure must never affect message delivery
            log.warn("Failed to publish MessageCreatedEvent for messageId={}: {}",
                    responseDTO.getMessageId(), e.getMessage());
        }
    }

    /**
     * Checks recipient presence and dispatches a push notification if offline.
     */
    private void notifyOfflineRecipient(
            String recipientId,
            String senderId,
            String content,
            String chatRoomId) {

        try {
            if (!userPresenceService.isUserConnected(recipientId)) {
                log.debug("Recipient {} is offline, dispatching push notification", recipientId);
                pushNotificationService.sendPushNotificationToUser(
                        recipientId,
                        senderId,
                        content,
                        chatRoomId);
            }
        } catch (Exception e) {
            log.warn("Failed to check presence or send push for recipient {}: {}",
                    recipientId, e.getMessage());
        }
    }

    /**
     * Extracts the sender ID from the authenticated Principal.
     */
    private String extractSenderId(Principal principal) {
        if (principal == null) {
            return null;
        }

        if (principal instanceof Authentication auth) {
            return auth.getName();
        }

        return principal.getName();
    }
}
