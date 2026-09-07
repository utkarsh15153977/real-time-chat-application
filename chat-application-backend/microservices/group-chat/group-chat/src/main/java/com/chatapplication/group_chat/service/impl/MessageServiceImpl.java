package com.chatapplication.group_chat.service.impl;

import com.chatapplication.group_chat.dto.request.ChatMessageRequest;
import com.chatapplication.group_chat.dto.request.EditMessageRequest;
import com.chatapplication.group_chat.dto.request.ReactionRequest;
import com.chatapplication.group_chat.dto.response.ChatMessageResponse;
import com.chatapplication.group_chat.dto.response.ReactionResponse;
import com.chatapplication.group_chat.entitty.ChatMessage;
import com.chatapplication.group_chat.entitty.Conversation;
import com.chatapplication.group_chat.entitty.MessageReaction;
import com.chatapplication.group_chat.entitty.MessageStatus;
import com.chatapplication.group_chat.exception.InvalidMessageException;
import com.chatapplication.group_chat.exception.MessageAlreadyDeletedException;
import com.chatapplication.group_chat.exception.MessageAlreadyEditedException;
import com.chatapplication.group_chat.exception.MessageNotFoundException;
import com.chatapplication.group_chat.exception.ReactionNotFoundException;
import com.chatapplication.group_chat.exception.UnauthorizedException;
import com.chatapplication.group_chat.mapper.ChatMessageMapper;
import com.chatapplication.group_chat.mapper.MessageReactionMapper;
import com.chatapplication.group_chat.repository.ChatMessageRepository;
import com.chatapplication.group_chat.repository.ConversationRepository;
import com.chatapplication.group_chat.repository.MessageReactionRepository;
import com.chatapplication.group_chat.service.MessageService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class MessageServiceImpl implements MessageService {

    private final ChatMessageRepository chatMessageRepository;
    private final ConversationRepository conversationRepository;

    private final ChatMessageMapper chatMessageMapper;

    private final MessageReactionRepository messageReactionRepository;
    private final MessageReactionMapper messageReactionMapper;

    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Returns message or throws exception.
     */
    private ChatMessage getMessageOrThrow(Long messageId) {

        return chatMessageRepository.findById(messageId)
                .orElseThrow(() ->
                        new MessageNotFoundException(
                                "Message not found with id: " + messageId
                        ));
    }

    /**
     * Finds existing conversation or creates a new one.
     */
    private Conversation getOrCreateConversation(
            Long senderId,
            Long receiverId) {

        return conversationRepository
                .findConversation(senderId, receiverId)
                .orElseGet(() -> {

                    Conversation conversation =
                            Conversation.builder()
                                    .user1Id(senderId)
                                    .user2Id(receiverId)
                                    .createdAt(LocalDateTime.now())
                                    .updatedAt(LocalDateTime.now())
                                    .archived(false)
                                    .deleted(false)
                                    .build();

                    return conversationRepository.save(conversation);
                });
    }

    /**
     * Validates message request.
     */
    private void validateMessage(ChatMessageRequest request) {

        if (request == null) {
            throw new InvalidMessageException(
                    "Message request cannot be null."
            );
        }

        if (request.getSenderId() == null) {
            throw new InvalidMessageException(
                    "Sender id is required."
            );
        }

        if (request.getReceiverId() == null) {
            throw new InvalidMessageException(
                    "Receiver id is required."
            );
        }

        if (request.getContent() == null
                || request.getContent().trim().isEmpty()) {

            throw new InvalidMessageException(
                    "Message cannot be empty."
            );
        }
    }

    @Override
    public ChatMessageResponse sendMessage(
            ChatMessageRequest request) {

        validateMessage(request);

        log.info("Sending message from {} to {}",
                request.getSenderId(),
                request.getReceiverId());

        Conversation conversation =
                getOrCreateConversation(
                        request.getSenderId(),
                        request.getReceiverId()
                );

        ChatMessage message =
                ChatMessage.builder()
                        .conversation(conversation)
                        .senderId(request.getSenderId())
                        .receiverId(request.getReceiverId())
                        .content(request.getContent())
                        .status(MessageStatus.SENT)
                        .edited(false)
                        .deleted(false)
                        .build();

        message = chatMessageRepository.save(message);

        conversation.setUpdatedAt(LocalDateTime.now());
        conversationRepository.save(conversation);

        ChatMessageResponse response =
                chatMessageMapper.toResponse(message);

        messagingTemplate.convertAndSendToUser(
                request.getReceiverId().toString(),
                "/queue/messages",
                response
        );

        log.info("Message {} sent successfully.",
                message.getId());

        return response;
    }

    @Override
    @Transactional
    public ChatMessageResponse getMessage(Long messageId) {

        log.info("Fetching message {}",
                messageId);

        ChatMessage message =
                getMessageOrThrow(messageId);

        return chatMessageMapper.toResponse(message);
    }
    @Override
    public ChatMessageResponse editMessage(
            Long messageId,
            EditMessageRequest request) {

        log.info("Editing message {}", messageId);

        ChatMessage message = getMessageOrThrow(messageId);

        if (message.isDeleted()) {
            throw new MessageAlreadyDeletedException(
                    "Cannot edit a deleted message."
            );
        }

        if (!message.getSenderId().equals(request.getUserId())) {
            throw new UnauthorizedException(
                    "You can edit only your own messages."
            );
        }

        if (request.getContent() == null
                || request.getContent().trim().isEmpty()) {

            throw new InvalidMessageException(
                    "Message content cannot be empty."
            );
        }

        message.setContent(request.getContent());
        message.setEdited(true);
        message.setEditedAt(LocalDateTime.now());

        ChatMessage updatedMessage = chatMessageRepository.save(message);

        ChatMessageResponse response =
                chatMessageMapper.toResponse(updatedMessage);

        messagingTemplate.convertAndSendToUser(
                updatedMessage.getReceiverId().toString(),
                "/queue/message-updates",
                response
        );

        log.info("Message {} edited successfully.", messageId);

        return response;
    }

    @Override
    public void deleteMessage(
            Long messageId,
            Long userId) {

        log.info("Deleting message {}", messageId);

        ChatMessage message = getMessageOrThrow(messageId);

        if (message.isDeleted()) {
            throw new MessageAlreadyDeletedException(
                    "Message already deleted."
            );
        }

        if (!message.getSenderId().equals(userId)) {
            throw new UnauthorizedException(
                    "You can delete only your own messages."
            );
        }

        message.setDeleted(true);
        message.setDeletedAt(LocalDateTime.now());

        chatMessageRepository.save(message);

        messagingTemplate.convertAndSendToUser(
                message.getReceiverId().toString(),
                "/queue/message-deleted",
                message.getId()
        );

        log.info("Message {} deleted successfully.", messageId);
    }

    @Override
    public void markDelivered(Long messageId) {

        ChatMessage message = getMessageOrThrow(messageId);

        if (message.getStatus() == MessageStatus.SENT) {

            message.setStatus(MessageStatus.DELIVERED);

            chatMessageRepository.save(message);

            messagingTemplate.convertAndSendToUser(
                    message.getSenderId().toString(),
                    "/queue/message-status",
                    chatMessageMapper.toResponse(message)
            );

            log.info("Message {} marked as delivered.", messageId);
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
        }
    }

    @Override
    public void markAllAsSeen(Long senderId, Long receiverId) {
        Conversation conversation = conversationRepository
                .findConversation(senderId, receiverId)
                .orElse(null);
        if (conversation != null) {
            List<ChatMessage> unread = chatMessageRepository
                    .findByConversationAndReceiverIdAndStatus(
                            conversation, receiverId, MessageStatus.SENT);
            unread.forEach(msg -> {
                msg.setStatus(MessageStatus.SEEN);
                msg.setSeenAt(LocalDateTime.now());
            });
            chatMessageRepository.saveAll(unread);
        }
    }

    @Override
    public List<ChatMessageResponse> getConversation(Long senderId, Long receiverId) {
        Conversation conversation = conversationRepository
                .findConversation(senderId, receiverId)
                .orElse(null);
        if (conversation == null) {
            return List.of();
        }
        return chatMessageMapper.toResponseList(
                chatMessageRepository.findByConversationOrderByCreatedAtAsc(conversation));
    }

    @Override
    public List<ChatMessageResponse> getRecentMessages(Long userId) {
        List<ChatMessage> sent = chatMessageRepository.findBySenderIdOrderByCreatedAtDesc(userId);
        List<ChatMessage> received = chatMessageRepository.findByReceiverIdOrderByCreatedAtDesc(userId);
        Set<Long> seen = new HashSet<>();
        List<ChatMessage> merged = new ArrayList<>();
        for (ChatMessage m : sent) {
            if (seen.add(m.getId())) merged.add(m);
        }
        for (ChatMessage m : received) {
            if (seen.add(m.getId())) merged.add(m);
        }
        merged.sort((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()));
        return chatMessageMapper.toResponseList(merged);
    }

    @Override
    public long getUnreadCount(Long senderId, Long receiverId) {
        Conversation conversation = conversationRepository
                .findConversation(senderId, receiverId)
                .orElse(null);
        if (conversation == null) return 0;
        return chatMessageRepository.countByConversationAndReceiverIdAndStatus(
                conversation, receiverId, MessageStatus.SENT);
    }

    @Override
    public ReactionResponse addReaction(ReactionRequest request) {
        ChatMessage message = getMessageOrThrow(request.getMessageId());
        Optional<MessageReaction> existing = messageReactionRepository
                .findByChatMessageAndUserId(message, request.getUserId());
        if (existing.isPresent()) {
            MessageReaction reaction = existing.get();
            reaction.setReactionType(request.getReactionType());
            reaction.setUpdatedAt(LocalDateTime.now());
            messageReactionRepository.save(reaction);
            return messageReactionMapper.toResponse(reaction);
        }
        MessageReaction reaction = messageReactionMapper.toEntity(request);
        reaction.setChatMessage(message);
        messageReactionRepository.save(reaction);
        return messageReactionMapper.toResponse(reaction);
    }

    @Override
    public void removeReaction(Long reactionId) {
        MessageReaction reaction = messageReactionRepository.findById(reactionId)
                .orElseThrow(() -> new ReactionNotFoundException("Reaction not found: " + reactionId));
        messageReactionRepository.delete(reaction);
    }

    @Override
    public List<ReactionResponse> getMessageReactions(Long messageId) {
        ChatMessage message = getMessageOrThrow(messageId);
        return messageReactionMapper.toResponseList(
                messageReactionRepository.findByChatMessage(message));
    }

    @Override
    public boolean exists(Long messageId) {
        return chatMessageRepository.existsByIdAndDeletedFalse(messageId);
    }
}