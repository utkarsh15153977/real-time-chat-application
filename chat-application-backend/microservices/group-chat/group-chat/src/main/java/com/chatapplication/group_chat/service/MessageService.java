package com.chatapplication.group_chat.service;

import com.chatapplication.group_chat.dto.request.ChatMessageRequest;
import com.chatapplication.group_chat.dto.request.EditMessageRequest;
import com.chatapplication.group_chat.dto.request.ReactionRequest;
import com.chatapplication.group_chat.dto.response.ChatMessageResponse;
import com.chatapplication.group_chat.dto.response.ReactionResponse;

import java.util.List;

public interface MessageService {

    /**
     * Send a private message.
     */
    ChatMessageResponse sendMessage(ChatMessageRequest request);

    /**
     * Get a message by id.
     */
    ChatMessageResponse getMessage(Long messageId);

    /**
     * Edit a previously sent message.
     */
    ChatMessageResponse editMessage(
            Long messageId,
            EditMessageRequest request
    );

    /**
     * Soft delete a message.
     */
    void deleteMessage(
            Long messageId,
            Long userId
    );

    /**
     * Mark a message as delivered.
     */
    void markDelivered(Long messageId);

    /**
     * Mark a message as seen.
     */
    void markSeen(Long messageId);

    /**
     * Mark all messages in a conversation as seen.
     */
    void markAllAsSeen(
            Long senderId,
            Long receiverId
    );

    /**
     * Get conversation messages between two users.
     */
    List<ChatMessageResponse> getConversation(
            Long senderId,
            Long receiverId
    );

    /**
     * Get recent messages of a user.
     */
    List<ChatMessageResponse> getRecentMessages(
            Long userId
    );

    /**
     * Get unread message count.
     */
    long getUnreadCount(
            Long senderId,
            Long receiverId
    );

    /**
     * Add or update a reaction.
     */
    ReactionResponse addReaction(
            ReactionRequest request
    );

    /**
     * Remove a reaction.
     */
    void removeReaction(
            Long reactionId
    );

    /**
     * Get all reactions of a message.
     */
    List<ReactionResponse> getMessageReactions(
            Long messageId
    );

    /**
     * Check whether a message exists.
     */
    boolean exists(Long messageId);
}