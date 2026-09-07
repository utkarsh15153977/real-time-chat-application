//package com.chatApplication.message_service.service;
//
//import com.chatApplication.message_service.dto.AttachmentRequest;
//import com.chatApplication.message_service.dto.MessageRequest;
//import com.chatApplication.message_service.dto.MessageResponse;
//import com.chatApplication.message_service.entity.MessageStatus;
//import com.chatApplication.message_service.repository.MessageRepository;
//import jakarta.transaction.Transactional;
//import lombok.RequiredArgsConstructor;
//import com.chatApplication.message_service.entity.Message;
//import org.springframework.stereotype.Service;
//
//import java.time.LocalDateTime;
//import java.util.List;
//
//@Service
//@RequiredArgsConstructor
//@Transactional
//public class MessageServiceImpl<Message> implements MessageService {
//
//    private final MessageRepository messageRepository;
//
//    @Override
//    public MessageResponse sendMessage(MessageRequest request) {
//
//        Message message = Message.builder()
//                .senderId(request.getSenderId())
//                .receiverId(request.getReceiverId())
//                .content(request.getContent())
//                .status(MessageStatus.SENT)
//                .createdAt(LocalDateTime.now())
//                .build();
//
//        return mapToResponse(
//                messageRepository.save(message));
//    }
//
//    @Override
//    public List<MessageResponse> getConversation(
//            String user1,
//            String user2) {
//
//        return messageRepository
//                .findConversation(user1, user2)
//                .stream()
//                .map(this::mapToResponse)
//                .toList();
//    }
//
//    @Override
//    public MessageResponse getMessage(Long messageId) {
//
//        Message message = messageRepository.findById(messageId)
//                .orElseThrow(() ->
//                        new RuntimeException("Message not found"));
//
//        return mapToResponse(message);
//    }
//
//    @Override
//    public MessageResponse markAsDelivered(Long messageId) {
//
//        Message message = messageRepository.findById(messageId)
//                .orElseThrow(() ->
//                        new RuntimeException("Message not found"));
//
//        message.setStatus(MessageStatus.DELIVERED);
//
//        return mapToResponse(
//                messageRepository.save(message));
//    }
//
//    @Override
//    public MessageResponse markAsSeen(Long messageId) {
//
//        Message message = messageRepository.findById(messageId)
//                .orElseThrow(() ->
//                        new RuntimeException("Message not found"));
//
//        message.setStatus(MessageStatus.SEEN);
//
//        return mapToResponse(
//                messageRepository.save(message));
//    }
//
//    @Override
//    public void markAllAsSeen(
//            String senderId,
//            String receiverId) {
//
//        List<Message> messages =
//                messageRepository.findUnreadMessages(
//                        senderId,
//                        receiverId);
//
//        messages.forEach(
//                msg -> msg.setStatus(MessageStatus.SEEN));
//
//        messageRepository.saveAll(messages);
//    }
//
//    @Override
//    public void deleteMessage(Long messageId) {
//
//        messageRepository.deleteById(messageId);
//    }
//
//    @Override
//    public MessageResponse editMessage(
//            Long messageId,
//            String content) {
//
//        Message message = messageRepository.findById(messageId)
//                .orElseThrow(() ->
//                        new RuntimeException("Message not found"));
//
//        message.setContent(content);
//        message.setEdited(true);
//
//        return mapToResponse(
//                messageRepository.save(message));
//    }
//
//    @Override
//    public Long getUnreadCount(
//            String senderId,
//            String receiverId) {
//
//        return messageRepository.countUnreadMessages(
//                senderId,
//                receiverId);
//    }
//
//    @Override
//    public List<MessageResponse> getRecentMessages(
//            String userId) {
//
//        return messageRepository
//                .findRecentMessages(userId)
//                .stream()
//                .map(this::mapToResponse)
//                .toList();
//    }
//
//    @Override
//    public boolean exists(Long messageId) {
//
//        return messageRepository.existsById(messageId);
//    }
//
//    @Override
//    public MessageResponse sendAttachment(
//            AttachmentRequest request) {
//
//        Message message = Message.builder()
//                .senderId(request.getSenderId())
//                .receiverId(request.getReceiverId())
//                .attachmentUrl(request.getAttachmentUrl())
//                .attachmentType(request.getAttachmentType())
//                .status(MessageStatus.SENT)
//                .createdAt(LocalDateTime.now())
//                .build();
//
//        return mapToResponse(
//                messageRepository.save(message));
//    }
//
//    private MessageResponse mapToResponse(
//            Message message) {
//
//        return MessageResponse.builder()
//                .id(message.getId())
//                .senderId(message.getSenderId())
//                .receiverId(message.getReceiverId())
//                .content(message.getContent())
//                .attachmentUrl(message.getAttachmentUrl())
//                .status(message.getStatus())
//                .createdAt(message.getCreatedAt())
//                .build();
//    }
//}

package com.chatApplication.message_service.service;

import com.chatApplication.message_service.dto.*;
import com.chatApplication.message_service.entity.Message;
import com.chatApplication.message_service.entity.MessageStatus;
import com.chatApplication.message_service.entity.MessageType;
import com.chatApplication.message_service.exception.MessageValidationException;
import com.chatApplication.message_service.kafka.MessageEvent;
import com.chatApplication.message_service.kafka.MessageProducer;
import com.chatApplication.message_service.repository.MessageRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Core message service implementation.
 * <p>
 * Handles message persistence, WebSocket delivery, Redis Pub/Sub
 * for cross-instance synchronization, and Kafka event publishing.
 * <p>
 * Delivery flow:
 *   1. Persist message to PostgreSQL with SENT status
 *   2. Send to receiver's STOMP queue via SimpMessagingTemplate (local)
 *   3. Publish to Redis "chat:messages" channel (cross-instance)
 *   4. Publish to Kafka "message-events" topic (async consumers)
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class MessageServiceImpl implements MessageService {

    private final MessageRepository messageRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final MessageProducer messageProducer;
    private final RedisMessagePublisher redisMessagePublisher;

//    @Override
//    public MessageResponse sendMessage(
//            MessageRequest request) {
//
//        Message message = Message.builder()
//                .senderId(request.getSenderId())
//                .receiverId(request.getReceiverId())
//                .content(request.getMessage())
//                .status(MessageStatus.SENT)
//                .isAttachment(false)
//                .build();
//
//        Message savedMessage =
//                messageRepository.save(message);
//
//        ChatMessage chatMessage =
//                ChatMessage.builder()
//                        .msgId(savedMessage.getMsgId())
//                        .senderId(savedMessage.getSenderId())
//                        .receiverId(savedMessage.getReceiverId())
//                        .content(savedMessage.getContent())
//                        .status(savedMessage.getStatus().name())
//                        .build();
//
//
//
//        messagingTemplate.convertAndSendToUser(
//                savedMessage.getReceiverId(),
//                "/queue/messages",
//                chatMessage);
//
//        return mapToResponse(savedMessage);
//    }

    @Override
    public MessageResponse sendMessage(
            MessageRequest request) {

        Message message = Message.builder()
                .senderId(request.getSenderId())
                .receiverId(request.getReceiverId())
                .content(request.getMessage())
                .status(MessageStatus.SENT)
                .isAttachment(false)
                .build();

        Message savedMessage =
                messageRepository.save(message);

        // Build the WebSocket payload DTO
        ChatMessage chatMessage =
                ChatMessage.builder()
                        .msgId(savedMessage.getMsgId())
                        .senderId(savedMessage.getSenderId())
                        .receiverId(savedMessage.getReceiverId())
                        .content(savedMessage.getContent())
                        .status(savedMessage.getStatus().name())
                        .build();

        // 1. Local WebSocket delivery: send to receiver's STOMP queue
        //    on this server instance
        messagingTemplate.convertAndSendToUser(
                savedMessage.getReceiverId(),
                "/queue/messages",
                chatMessage);

        // 2. Redis Pub/Sub: publish to "chat:messages" channel so all
        //    other message-service instances relay the message to their
        //    connected WebSocket clients
        redisMessagePublisher.publish(chatMessage);

        // 3. Kafka: publish event for async consumers (notifications, analytics, etc.)
        messageProducer.publish(
                MessageEvent.builder()
                        .messageId(savedMessage.getMsgId())
                        .senderId(savedMessage.getSenderId())
                        .receiverId(savedMessage.getReceiverId())
                        .content(savedMessage.getContent())
                        .status(savedMessage.getStatus().name())
                        .build()
        );

        return mapToResponse(savedMessage);
    }

    // ----------------------------------------------------------------
    // Media Messaging: saveMessage and getChatHistory
    // ----------------------------------------------------------------

    /** MIME type prefixes allowed for media messages */
    private static final Set<String> ALLOWED_MEDIA_TYPES = Set.of(
            "image/", "video/", "audio/"
    );

    /**
     * {@inheritDoc}
     * <p>
     * Validates that non-TEXT messages include required media fields,
     * defaults messageType to TEXT if null, persists the entity, and
     * returns the response DTO.
     */
    @Override
    @Transactional
    public ChatMessageResponseDTO saveMessage(ChatMessageRequestDTO requestDTO) {
        // Default messageType to TEXT if null
        MessageType messageType = requestDTO.getMessageType() != null
                ? requestDTO.getMessageType()
                : MessageType.TEXT;

        // Validate media fields for non-TEXT messages
        validateMediaFields(requestDTO, messageType);

        // Build and persist the entity
        Message message = Message.builder()
                .senderId(requestDTO.getSenderId())
                .receiverId(requestDTO.getRecipientId())
                .content(requestDTO.getContent())
                .messageType(messageType)
                .mediaUrl(requestDTO.getMediaUrl())
                .fileKey(requestDTO.getFileKey())
                .fileSizeBytes(requestDTO.getFileSizeBytes())
                .status(MessageStatus.SENT)
                .build();

        Message saved = messageRepository.save(message);

        log.info("Message saved: id={}, type={}, sender={}, receiver={}",
                saved.getMsgId(), messageType,
                saved.getSenderId(), saved.getReceiverId());

        return mapToResponseDTO(saved);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns messages in reverse chronological order (newest first)
     * for efficient pagination.
     */
    @Override
    @Transactional(readOnly = true)
    public Page<ChatMessageResponseDTO> getChatHistory(
            String chatRoomId, Pageable pageable) {

        // Chat room ID format: "{userId1}-{userId2}" for 1:1 chats
        // Parse the two user IDs from the chat room ID
        String[] userIds = parseChatRoomId(chatRoomId);

        return messageRepository
                .findConversationPaged(userIds[0], userIds[1], pageable)
                .map(this::mapToResponseDTO);
    }

    /**
     * Validates that non-TEXT messages have required media fields.
     *
     * @param requestDTO the incoming request
     * @param messageType the resolved message type
     * @throws MessageValidationException if required fields are missing
     */
    private void validateMediaFields(
            ChatMessageRequestDTO requestDTO,
            MessageType messageType) {

        if (messageType != MessageType.TEXT) {
            if (requestDTO.getMediaUrl() == null
                    || requestDTO.getMediaUrl().isBlank()) {
                throw new MessageValidationException(
                        "mediaUrl is required for " + messageType + " messages");
            }
            if (requestDTO.getFileKey() == null
                    || requestDTO.getFileKey().isBlank()) {
                throw new MessageValidationException(
                        "fileKey is required for " + messageType + " messages");
            }
        }

        // TEXT messages should have content
        if (messageType == MessageType.TEXT
                && (requestDTO.getContent() == null
                || requestDTO.getContent().isBlank())) {
            throw new MessageValidationException(
                    "content is required for TEXT messages");
        }
    }

    /**
     * Parses a chat room ID into its constituent user IDs.
     * Expects format: "{userId1}-{userId2}"
     *
     * @param chatRoomId the chat room identifier
     * @return array of two user ID strings
     * @throws MessageValidationException if the format is invalid
     */
    private String[] parseChatRoomId(String chatRoomId) {
        if (chatRoomId == null || chatRoomId.isBlank()) {
            throw new MessageValidationException("chatRoomId is required");
        }
        String[] parts = chatRoomId.split("-");
        if (parts.length != 2) {
            throw new MessageValidationException(
                    "Invalid chatRoomId format. Expected: {userId1}-{userId2}");
        }
        return parts;
    }

    /**
     * Maps a Message entity to a ChatMessageResponseDTO.
     *
     * @param message the persisted entity
     * @return the response DTO
     */
    private ChatMessageResponseDTO mapToResponseDTO(Message message) {
        return ChatMessageResponseDTO.builder()
                .messageId(String.valueOf(message.getMsgId()))
                .senderId(message.getSenderId())
                .recipientId(message.getReceiverId())
                .chatRoomId(message.getSenderId() + "-" + message.getReceiverId())
                .content(message.getContent())
                .messageType(message.getMessageType())
                .mediaUrl(message.getMediaUrl())
                .fileKey(message.getFileKey())
                .fileSizeBytes(message.getFileSizeBytes())
                .status(message.getStatus() != null
                        ? message.getStatus().name() : null)
                .timestamp(message.getTimestamp() != null
                        ? message.getTimestamp().toInstant(java.time.ZoneOffset.UTC)
                        : Instant.now())
                .build();
    }

    // ----------------------------------------------------------------
    // Existing methods (preserved)
    // ----------------------------------------------------------------

    @Override
    public List<MessageResponse> getConversation(
            String user1,
            String user2) {

        List<MessageResponse> responses = new ArrayList<>();

        List<Message> messages =
                messageRepository.findConversation(
                        user1,
                        user2);

        for (Message message : messages) {
            responses.add(
                    mapToResponse(message));
        }

        return responses;
    }

    @Override
    public MessageResponse getMessage(Long messageId) {

        Message message =
                messageRepository.findById(messageId)
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Message not found"));

        return mapToResponse(message);
    }

//    @Override
//    public MessageResponse markAsDelivered(Long messageId) {
//
//        Message message =
//                messageRepository.findById(messageId)
//                        .orElseThrow(() ->
//                                new RuntimeException(
//                                        "Message not found"));
//
//        message.setStatus(
//                MessageStatus.DELIVERED);
//
//        return mapToResponse(
//                messageRepository.save(message));
//    }

    @Override
    public MessageResponse markAsDelivered(
            Long messageId) {

        Message message =
                messageRepository.findById(messageId)
                        .orElseThrow();

        if(message.getStatus() ==
                MessageStatus.SENT){

            message.setStatus(
                    MessageStatus.DELIVERED);
        }

        Message updated =
                messageRepository.save(message);

        sendReceipt(updated);

        return mapToResponse(updated);
    }

//    @Override
//    public MessageResponse markAsSeen(Long messageId) {
//
//        Message message =
//                messageRepository.findById(messageId)
//                        .orElseThrow(() ->
//                                new RuntimeException(
//                                        "Message not found"));
//
//        message.setStatus(
//                MessageStatus.SEEN);
//
//        return mapToResponse(
//                messageRepository.save(message));
//    }

    @Override
    public MessageResponse markAsSeen(
            Long messageId) {

        Message message =
                messageRepository.findById(messageId)
                        .orElseThrow();

        message.setStatus(
                MessageStatus.READ);

        Message updated =
                messageRepository.save(message);

        sendReceipt(updated);

        return mapToResponse(updated);
    }

    @Override
    public void markAllAsSeen(
            String senderId,
            String receiverId) {

        List<Message> messages =
                messageRepository.findUnreadMessages(
                        senderId,
                        receiverId);

        for (Message message : messages) {

            message.setStatus(
                    MessageStatus.READ);
        }

        messageRepository.saveAll(messages);
    }

    @Override
    public void deleteMessage(Long messageId) {

        if (!messageRepository.existsById(messageId)) {

            throw new RuntimeException(
                    "Message not found");
        }

        messageRepository.deleteById(messageId);
    }

    @Override
    public MessageResponse editMessage(
            Long messageId,
            String content) {

        Message message =
                messageRepository.findById(messageId)
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Message not found"));

        message.setContent(content);

        return mapToResponse(
                messageRepository.save(message));
    }

    @Override
    public Long getUnreadCount(
            String senderId,
            String receiverId) {

        return messageRepository.countUnreadMessages(
                senderId,
                receiverId);
    }

    @Override
    public List<MessageResponse> getRecentMessages(
            String userId) {

        List<MessageResponse> responses =
                new ArrayList<>();

        List<Message> messages =
                messageRepository.findRecentMessages(
                        userId);

        for (Message message : messages) {

            responses.add(
                    mapToResponse(message));
        }

        return responses;
    }

    @Override
    public boolean exists(Long messageId) {

        return messageRepository.existsById(
                messageId);
    }

    @Override
    public MessageResponse sendAttachment(
            AttachmentRequest request) {

        Message message = Message.builder()
                .senderId(request.getSenderId())
                .receiverId(request.getReceiverId())
                .status(MessageStatus.SENT)
                .isAttachment(true)
                .attachmentName(
                        request.getFile()
                                .getOriginalFilename())
                .attachmentSize(
                        request.getFile()
                                .getSize())
                .attachmentType(
                        request.getFile()
                                .getContentType())
                .attachmentUrl(
                        "/uploads/"
                                + request.getFile()
                                .getOriginalFilename())
                .build();

        return mapToResponse(
                messageRepository.save(message));
    }

//    private MessageResponse mapToResponse(
//            Message message) {
//
//        return MessageResponse.builder()
//                .msgId(message.getMsgId())
//                .senderId(message.getSenderId())
//                .receiverId(message.getReceiverId())
//                .content(message.getContent())
//                .sendTime(message.getTimestamp())
//                .delivered(
//                        message.getStatus()
//                                == MessageStatus.DELIVERED
//                                || message.getStatus()
//                                == MessageStatus.SEEN)
//                .seen(
//                        message.getStatus()
//                                == MessageStatus.SEEN)
//                .build();
//    }

    private MessageResponse mapToResponse(
            Message message) {

        return MessageResponse.builder()
                .msgId(message.getMsgId())
                .senderId(message.getSenderId())
                .receiverId(message.getReceiverId())
                .content(message.getContent())
                .sendTime(message.getTimestamp())
                .status(message.getStatus())
                .build();
    }

    private void sendReceipt(
            Message message) {

        ReadReceiptEvent receipt =
                ReadReceiptEvent.builder()
                        .messageId(message.getMsgId())
                        .senderId(message.getSenderId())
                        .receiverId(message.getReceiverId())
                        .status(message.getStatus().name())
                        .build();

        messagingTemplate.convertAndSendToUser(
                message.getSenderId(),
                "/queue/receipts",
                receipt);
    }
}