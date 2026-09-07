package com.chatApplication.message_service.service;

import com.chatApplication.message_service.dto.DeliveryAckEvent;
import com.chatApplication.message_service.entity.Message;
import com.chatApplication.message_service.entity.MessageStatus;
import com.chatApplication.message_service.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Handles delivery acknowledgment (ACK) events from WebSocket clients.
 * <p>
 * Message delivery state machine:
 * <pre>
 *   SENT ──────> DELIVERED ──────> READ
 *     │                              ^
 *     └────────> FAILED ────────────┘ (manual retry/resend)
 * </pre>
 * <p>
 * DELIVERED_ACK flow:
 *   1. Recipient's client receives the message via WebSocket
 *   2. Client sends ACK to /app/chat.deliveredAck with messageId
 *   3. This service updates DB status: SENT -> DELIVERED
 *   4. Sends DeliveryAckEvent to sender via /queue/receipts
 *   5. Sender's UI updates the message status indicator
 * <p>
 * READ_ACK flow:
 *   1. Recipient opens the chat room / conversation
 *   2. Client sends ACK to /app/chat.readAck with senderId (the other party)
 *   3. This service bulk-updates DB status: SENT/DELIVERED -> READ
 *   4. Sends DeliveryAckEvent to sender via /queue/receipts
 *   5. Sender's UI updates all message status indicators
 * <p>
 * State validation ensures:
 * - DELIVERED only transitions from SENT (idempotent)
 * - READ transitions from SENT or DELIVERED (idempotent)
 * - FAILED can transition to any state on retry
 * - No backwards transitions (READ -> DELIVERED is rejected)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeliveryAckHandler {

    private final MessageRepository messageRepository;
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Processes a DELIVERED_ACK from a recipient's client.
     * <p>
     * Validates the state transition is legal:
     * - SENT -> DELIVERED: Valid, updates status
     * - DELIVERED -> DELIVERED: Idempotent, no-op
     * - READ -> DELIVERED: Invalid, rejected (already past this state)
     *
     * @param messageId   the message ID being acknowledged
     * @param recipientId the user ID of the recipient who received the message
     */
    @Transactional
    public void handleDeliveredAck(Long messageId, String recipientId) {
        log.info("Processing DELIVERED_ACK for message {} from user {}",
                messageId, recipientId);

        Message message = messageRepository.findById(messageId)
                .orElse(null);

        if (message == null) {
            log.warn("DELIVERED_ACK ignored: message {} not found", messageId);
            return;
        }

        // Validate state transition: only SENT -> DELIVERED is allowed
        if (message.getStatus() == MessageStatus.READ) {
            log.debug("DELIVERED_ACK ignored: message {} already READ", messageId);
            return;
        }

        if (message.getStatus() == MessageStatus.DELIVERED) {
            log.debug("DELIVERED_ACK idempotent: message {} already DELIVERED", messageId);
            return;
        }

        // Transition: SENT -> DELIVERED
        message.setStatus(MessageStatus.DELIVERED);
        messageRepository.save(message);

        // Send ACK event back to the sender
        DeliveryAckEvent ackEvent = DeliveryAckEvent.builder()
                .messageId(message.getMsgId())
                .senderId(message.getSenderId())
                .recipientId(recipientId)
                .status(MessageStatus.DELIVERED)
                .timestamp(LocalDateTime.now())
                .build();

        // Target the sender's receipt queue
        messagingTemplate.convertAndSendToUser(
                message.getSenderId(),
                "/queue/receipts",
                ackEvent);

        log.info("Message {} marked DELIVERED, ACK sent to sender {}",
                messageId, message.getSenderId());
    }

    /**
     * Processes a READ_ACK from a recipient's client.
     * <p>
     * Bulk-updates all unread messages in a conversation where
     * the sender is the specified senderId and recipient is this user.
     * <p>
     * State transitions:
     * - SENT -> READ: Valid, skips DELIVERED intermediate state
     * - DELIVERED -> READ: Valid, normal progression
     * - READ -> READ: Idempotent, no-op
     *
     * @param senderId    the user ID of the message sender (the other party)
     * @param recipientId the user ID of the recipient who opened the chat
     */
    @Transactional
    public void handleReadAck(String senderId, String recipientId) {
        log.info("Processing READ_ACK: sender={}, recipient={}",
                senderId, recipientId);

        // Find all unread messages in this conversation
        List<Message> messages = messageRepository
                .findUnreadMessagesInConversation(senderId, recipientId);

        if (messages.isEmpty()) {
            log.debug("READ_ACK: no unread messages from {} to {}",
                    senderId, recipientId);
            return;
        }

        int updatedCount = 0;
        for (Message message : messages) {
            // Only transition from SENT or DELIVERED to READ
            if (message.getStatus() == MessageStatus.SENT
                    || message.getStatus() == MessageStatus.DELIVERED) {
                message.setStatus(MessageStatus.READ);
                updatedCount++;
            }
        }

        if (updatedCount > 0) {
            messageRepository.saveAll(messages);
        }

        // Send a single ACK event to the sender with the most recent message ID
        if (!messages.isEmpty()) {
            Message mostRecent = messages.get(messages.size() - 1);

            DeliveryAckEvent ackEvent = DeliveryAckEvent.builder()
                    .messageId(mostRecent.getMsgId())
                    .senderId(senderId)
                    .recipientId(recipientId)
                    .status(MessageStatus.READ)
                    .timestamp(LocalDateTime.now())
                    .build();

            // Target the sender's receipt queue
            messagingTemplate.convertAndSendToUser(
                    senderId,
                    "/queue/receipts",
                    ackEvent);
        }

        log.info("READ_ACK: {} messages from {} to {} marked READ",
                updatedCount, senderId, recipientId);
    }

    /**
     * Marks a message as FAILED (e.g., after delivery timeout or error).
     * This allows the system to retry delivery or notify the sender.
     *
     * @param messageId the message ID that failed to deliver
     */
    @Transactional
    public void markAsFailed(Long messageId) {
        log.warn("Marking message {} as FAILED", messageId);

        Message message = messageRepository.findById(messageId)
                .orElse(null);

        if (message == null) {
            log.warn("Cannot mark FAILED: message {} not found", messageId);
            return;
        }

        // Only SENT messages can fail (DELIVERED/READ are already acknowledged)
        if (message.getStatus() == MessageStatus.SENT) {
            message.setStatus(MessageStatus.FAILED);
            messageRepository.save(message);

            // Notify sender of failure
            DeliveryAckEvent ackEvent = DeliveryAckEvent.builder()
                    .messageId(message.getMsgId())
                    .senderId(message.getSenderId())
                    .status(MessageStatus.FAILED)
                    .timestamp(LocalDateTime.now())
                    .build();

            messagingTemplate.convertAndSendToUser(
                    message.getSenderId(),
                    "/queue/receipts",
                    ackEvent);

            log.warn("Message {} marked FAILED, notification sent to sender {}",
                    messageId, message.getSenderId());
        }
    }
}
