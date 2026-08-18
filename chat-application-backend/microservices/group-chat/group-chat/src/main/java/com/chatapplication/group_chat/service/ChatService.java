package com.chatapplication.group_chat.service;

import com.chatapplication.group_chat.dto.request.*;
import com.chatapplication.group_chat.dto.response.ChatMessageResponse;
import com.chatapplication.group_chat.dto.response.ConversationResponse;
import com.chatapplication.group_chat.dto.response.ReactionResponse;

import java.util.List;

public interface ChatService {
    /**
     * Send a private message.
     */
    ChatMessageResponse sendMessage(ChatMessageRequest request);

    /**
     * Edit an existing message.
     */
    ChatMessageResponse editMessage(EditMessageRequest request);

    /**
     * Delete a message.
     */
    void deleteMessage(DeleteMessageRequest request);

    /**
     * Get a message by its ID.
     */
    ChatMessageResponse getMessage(Long messageId);

    /**
     * Get conversation between two users.
     */
    List<ChatMessageResponse> getConversation(Long senderId, Long receiverId);

    /**
     * Get all conversations of a user.
     */
    List<ConversationResponse> getUserConversations(Long userId);

    /**
     * Mark message as delivered.
     */
    void markDelivered(Long messageId);

    /**
     * Mark message as seen.
     */
    void markSeen(Long messageId);

    /**
     * Mark all messages from sender as seen.
     */
    void markAllSeen(Long senderId, Long receiverId);

    /**
     * Get unread message count.
     */
    Long getUnreadCount(Long senderId, Long receiverId);

    /**
     * Add or update reaction.
     */
    ReactionResponse addReaction(ReactionRequest request);

    /**
     * Remove reaction.
     */
    void removeReaction(Long reactionId);

    /**
     * Get reactions for a message.
     */
    List<ReactionResponse> getMessageReactions(Long messageId);
}
