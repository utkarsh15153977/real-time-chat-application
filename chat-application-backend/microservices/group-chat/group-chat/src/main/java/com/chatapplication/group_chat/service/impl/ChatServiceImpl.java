package com.chatapplication.group_chat.service.impl;

import com.chatapplication.group_chat.dto.request.ChatMessageRequest;
import com.chatapplication.group_chat.dto.request.DeleteMessageRequest;
import com.chatapplication.group_chat.dto.request.EditMessageRequest;
import com.chatapplication.group_chat.dto.request.ReactionRequest;
import com.chatapplication.group_chat.dto.response.ChatMessageResponse;
import com.chatapplication.group_chat.dto.response.ConversationResponse;
import com.chatapplication.group_chat.dto.response.ReactionResponse;
import com.chatapplication.group_chat.entitty.ChatMessage;
import com.chatapplication.group_chat.entitty.Conversation;
import com.chatapplication.group_chat.entitty.MessageReaction;
import com.chatapplication.group_chat.entitty.MessageStatus;
import com.chatapplication.group_chat.exception.*;
import com.chatapplication.group_chat.mapper.ChatMessageMapper;
import com.chatapplication.group_chat.mapper.ConversationMapper;
import com.chatapplication.group_chat.mapper.MessageReactionMapper;
import com.chatapplication.group_chat.repository.ChatMessageRepository;
import com.chatapplication.group_chat.repository.ConversationRepository;
import com.chatapplication.group_chat.repository.MessageReactionRepository;
import com.chatapplication.group_chat.service.ChatService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ChatServiceImpl implements ChatService {

    private final ChatMessageRepository chatMessageRepository;
    private final ConversationRepository conversationRepository;
    private final ChatMessageMapper chatMessageMapper;
    private final ConversationMapper conversationMapper;
    private final SimpMessagingTemplate messagingTemplate;
    private final MessageReactionRepository messageReactionRepository;
    private final MessageReactionMapper messageReactionMapper;
    /**
     * Find message by id.
     */
//    private ChatMessage getMessageOrThrow(Long messageId) {
//
////        return chatMessageRepository.findById(messageId)
////                .orElseThrow(() ->
////                        new RuntimeException(
////                                "Message not found : " + messageId
////                        ));
//        return chatMessageRepository.findById(messageId)
//                .orElseThrow(() -> new MessageNotFoundException(messageId));
//    }
    private ChatMessage getMessageOrThrow(Long messageId) {

        return chatMessageRepository.findById(messageId)
                .orElseThrow(() ->
                        new MessageNotFoundException(
                                "Message not found with id: " + messageId
                        )
                );
    }
    /**
     * Find existing conversation or create new one.
     */
    private Conversation getOrCreateConversation(Long senderId, Long receiverId) {

        return conversationRepository
                .findConversation(senderId, receiverId)
                .orElseGet(() -> {

//                    Conversation conversation = Conversation.builder()
//                            .senderId(senderId)
//                            .receiverId(receiverId)
//                            .createdAt(LocalDateTime.now())
//                            .updatedAt(LocalDateTime.now())
//                            .build();

                    Conversation conversation = Conversation.builder()
                            .user1Id(senderId)
                            .user2Id(receiverId)
                            .createdAt(LocalDateTime.now())
                            .updatedAt(LocalDateTime.now())
                            .build();
                    return conversationRepository.save(conversation);
                });
    }

    /**
     * Update conversation after sending/editing message.
     */
    private void updateConversation(Conversation conversation,
                                    ChatMessage message) {
        conversation.setLastMessage(message);
        conversation.setUpdatedAt(LocalDateTime.now());
        conversationRepository.save(conversation);
    }

    /**
     * Send websocket message.
     */
    private void sendRealtimeMessage(ChatMessage message) {

        messagingTemplate.convertAndSendToUser(
                message.getReceiverId().toString(),
                "/queue/messages",
                chatMessageMapper.toResponse(message)
        );
    }

    /**
     * Publish Kafka event.
     */
    private void publishMessageEvent(ChatMessage message) {
        //kafkaProducerService.publishMessage(message);
        log.info("Publishing message {} to Kafka", message.getId());
    }

    /**
     * Check if receiver is online.
     */
    private boolean isReceiverOnline(Long receiverId) {
        //return redisPresenceService.isOnline(receiverId);
        return false;
    }
    /**
     * Mark delivered if receiver is online.
     */
    private void markDeliveredIfOnline(ChatMessage message) {
        if (isReceiverOnline(message.getReceiverId())) {
            message.setStatus(MessageStatus.DELIVERED);
            chatMessageRepository.save(message);
        }
    }
    @Override
    public ChatMessageResponse sendMessage(ChatMessageRequest request) {

        log.info("Sending message from {} to {}",
                request.getSenderId(),
                request.getReceiverId());

        // -----------------------------------------
        // Validate Request
        // -----------------------------------------

        if (request.getSenderId() == null) {
            throw new InvalidMessageException("Sender Id is required.");
        }
        if (request.getReceiverId() == null) {
            throw new InvalidMessageException("Receiver Id is required.");
        }
        if (request.getContent() == null ||
                request.getContent().trim().isEmpty()) {
            throw new InvalidMessageException("Message cannot be empty.");
        }

        // -----------------------------------------
        // Find or Create Conversation
        // -----------------------------------------

        Conversation conversation =
                getOrCreateConversation(
                        request.getSenderId(),
                        request.getReceiverId());
        // -----------------------------------------
        // Convert DTO -> Entity
        // -----------------------------------------

        ChatMessage message =
                chatMessageMapper.toEntity(request);
        message.setConversation(conversation);
        message.setSenderId(request.getSenderId());
        message.setReceiverId(request.getReceiverId());
        message.setContent(request.getContent());
        message.setStatus(MessageStatus.SENT);
        message.setCreatedAt(LocalDateTime.now());

        // -----------------------------------------
        // Save Message
        // -----------------------------------------

        message = chatMessageRepository.save(message);
        log.info("Message {} saved successfully",
                message.getId());
        // -----------------------------------------
        // Update Conversation
        // -----------------------------------------

        updateConversation(conversation, message);

        // -----------------------------------------
        // Receiver Online?
        // -----------------------------------------

        if (isReceiverOnline(request.getReceiverId())) {
            message.setStatus(MessageStatus.DELIVERED);
            message = chatMessageRepository.save(message);
            log.info("Receiver online. Message delivered.");
        }
        // -----------------------------------------
        // Publish Kafka Event
        // -----------------------------------------
        publishMessageEvent(message);
        // -----------------------------------------
        // Send WebSocket Message
        // -----------------------------------------
        sendRealtimeMessage(message);
        // -----------------------------------------
        // Notification Service
        // -----------------------------------------
//    notificationClient.sendNotification(
//            message.getReceiverId(),
//            "New Message"
//    );
        // -----------------------------------------
        // Return Response
        // -----------------------------------------
        ChatMessageResponse response =
                chatMessageMapper.toResponse(message);
        log.info("Message {} sent successfully",
                message.getId());

        return response;
    }

    @Override
    public ChatMessageResponse editMessage(EditMessageRequest request) {

        log.info("Editing message {}", request.getMessageId());

        ChatMessage message = getMessageOrThrow(request.getMessageId());

        // -----------------------------------------
        // Ownership Validation
        // -----------------------------------------

        if (!message.getSenderId().equals(request.getUserId())) {
            throw new UnauthorizedException("Only the sender can edit this message.");
        }

        // -----------------------------------------
        // Deleted messages cannot be edited
        // -----------------------------------------

        if (Boolean.TRUE.equals(message.isDeleted())) {
            throw new MessageAlreadyDeletedException();
        }

        // -----------------------------------------
        // Already edited (Optional Business Rule)
        // -----------------------------------------

        if (Boolean.TRUE.equals(message.isEdited())) {
            throw new MessageAlreadyEditedException();
        }

        // -----------------------------------------
        // Empty message validation
        // -----------------------------------------

        if (request.getContent() == null ||
                request.getContent().trim().isEmpty()) {
            throw new InvalidMessageException("Message cannot be empty.");
        }

        // -----------------------------------------
        // Update message
        // -----------------------------------------

        message.setContent(request.getContent().trim());
        message.setEdited(true);
        message.setEditedAt(LocalDateTime.now());

        message = chatMessageRepository.save(message);

        // -----------------------------------------
        // Update conversation
        // -----------------------------------------

        updateConversation(message.getConversation(), message);

        // -----------------------------------------
        // Publish Kafka Event
        // -----------------------------------------

        // kafkaProducerService.publishMessageEdited(message);
        log.info("Published message edited event for message {}", message.getId());

        // -----------------------------------------
        // Notify Receiver via WebSocket
        // -----------------------------------------

        messagingTemplate.convertAndSendToUser(
                message.getReceiverId().toString(),
                "/queue/message-edited",
                chatMessageMapper.toResponse(message)
        );

        log.info("Message {} edited successfully.", message.getId());

        return chatMessageMapper.toResponse(message);
    }

    @Override
    public void deleteMessage(DeleteMessageRequest request) {
        log.info("Deleting message {}", request.getMessageId());
        ChatMessage message = getMessageOrThrow(request.getMessageId());

        // -----------------------------------------
        // Ownership Validation
        // -----------------------------------------

        if (!message.getSenderId().equals(request.getUserId())) {
            throw new RuntimeException("Only sender can delete message.");
        }

        // -----------------------------------------
        // Already deleted
        // -----------------------------------------

        if (Boolean.TRUE.equals(message.isDeleted())) {
            return;
        }

        // -----------------------------------------
        // Soft Delete
        // -----------------------------------------

        message.setDeleted(true);
        message.setDeletedAt(LocalDateTime.now());
        message.setContent("This message was deleted");
        chatMessageRepository.save(message);

        // -----------------------------------------
        // Update conversation
        // -----------------------------------------

        updateConversation(message.getConversation(), message);

        // -----------------------------------------
        // Publish Kafka Event
        // -----------------------------------------

//    kafkaProducerService.publishMessageDeleted(message);

        log.info("Published message deleted event.");

        // -----------------------------------------
        // Notify Receiver
        // -----------------------------------------

        messagingTemplate.convertAndSendToUser(
                message.getReceiverId().toString(),
                "/queue/message-deleted",
                message.getId()
        );
        log.info("Message {} deleted successfully.", message.getId());
    }

    @Override
    @Transactional
    public ChatMessageResponse getMessage(Long messageId) {
        log.info("Fetching message {}", messageId);
        ChatMessage message = getMessageOrThrow(messageId);
        return chatMessageMapper.toResponse(message);
    }

    @Override
    @Transactional
    public List<ChatMessageResponse> getConversation(Long senderId,
                                                     Long receiverId) {

        log.info("Fetching conversation between {} and {}",
                senderId,
                receiverId);

        List<ChatMessage> messages =
                chatMessageRepository.findConversation(
                        senderId,
                        receiverId
                );

        return chatMessageMapper.toResponseList(messages);
    }

    @Override
    @Transactional
    public List<ConversationResponse> getUserConversations(Long userId) {

        log.info("Fetching conversations of user {}", userId);

        List<Conversation> conversations =
                conversationRepository.findBySenderIdOrReceiverId(
                        userId,
                        userId
                );

        return conversationMapper.toResponseList(conversations);
    }

    @Override
    @Transactional
    public Long getUnreadCount(Long senderId,
                               Long receiverId) {
        log.info("Fetching unread count");
        return chatMessageRepository.countUnreadMessages(
                senderId,
                receiverId
        );
    }

    @Override
    public void markDelivered(Long messageId) {
        ChatMessage message = getMessageOrThrow(messageId);
        if (message.getStatus() == MessageStatus.SENT) {
            message.setStatus(MessageStatus.DELIVERED);
            message.setDeliveredAt(LocalDateTime.now());
            chatMessageRepository.save(message);
            messagingTemplate.convertAndSendToUser(
                    message.getSenderId().toString(),
                    "/queue/message-status",
                    chatMessageMapper.toResponse(message)
            );
            log.info("Message {} marked delivered", messageId);
        }
    }

    @Override
    public void markSeen(Long messageId) {
        ChatMessage message = getMessageOrThrow(messageId);
        if (message.getStatus() != MessageStatus.SEEN) {
            message.setStatus(MessageStatus.SEEN);
            message.setSeenAt(LocalDateTime.now());
            chatMessageRepository.save(message);
            messagingTemplate.convertAndSendToUser(
                    message.getSenderId().toString(),
                    "/queue/message-status",
                    chatMessageMapper.toResponse(message)
            );
            log.info("Message {} marked seen", messageId);
        }
    }

    @Override
    public void markAllSeen(Long senderId,
                            Long receiverId) {
        List<ChatMessage> unreadMessages =
                chatMessageRepository.findUnreadMessages(
                        senderId,
                        receiverId
                );
        for (ChatMessage message : unreadMessages) {
            message.setStatus(MessageStatus.SEEN);
            message.setSeenAt(LocalDateTime.now());
        }
        chatMessageRepository.saveAll(unreadMessages);
        log.info("{} messages marked seen", unreadMessages.size());
    }
    @Override
    public ReactionResponse addReaction(ReactionRequest request) {

        log.info("Adding reaction {} on message {} by user {}",
                request.getReactionType(),
                request.getMessageId(),
                request.getUserId());

        ChatMessage message = getMessageOrThrow(request.getMessageId());

        MessageReaction reaction = messageReactionRepository
                .findByChatMessageAndUserId(
                        message,
                        request.getUserId()
                )
                .orElseGet(() -> {

                    MessageReaction newReaction =
                            messageReactionMapper.toEntity(request);

                    newReaction.setChatMessage(message);
                    newReaction.setUserId(request.getUserId());

                    return newReaction;
                });

        reaction.setReactionType(request.getReactionType());

        reaction = messageReactionRepository.save(reaction);

        messagingTemplate.convertAndSendToUser(
                message.getSenderId().toString(),
                "/queue/message-reactions",
                messageReactionMapper.toResponse(reaction)
        );

        log.info("Reaction added successfully.");

        return messageReactionMapper.toResponse(reaction);
    }

    @Override
    public void removeReaction(Long reactionId) {

        MessageReaction reaction =
                messageReactionRepository.findById(reactionId)
                        .orElseThrow(() ->
                                new ReactionNotFoundException(
                                        "Reaction not found : " + reactionId
                                ));

        ChatMessage message = reaction.getChatMessage();

        messageReactionRepository.delete(reaction);

        messagingTemplate.convertAndSendToUser(
                message.getSenderId().toString(),
                "/queue/message-reactions",
                reactionId
        );

        log.info("Reaction {} removed.", reactionId);
    }

    @Override
    @Transactional
    public List<ReactionResponse> getMessageReactions(Long messageId) {

        ChatMessage message = getMessageOrThrow(messageId);

        List<MessageReaction> reactions =
                messageReactionRepository.findByChatMessage(message);

        return messageReactionMapper.toResponseList(reactions);
    }
}