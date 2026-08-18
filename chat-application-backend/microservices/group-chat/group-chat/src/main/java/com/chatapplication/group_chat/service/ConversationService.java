package com.chatapplication.group_chat.service;

import com.chatapplication.group_chat.dto.response.ConversationResponse;

import java.util.List;

public interface ConversationService {
    /**
     * Create a conversation between two users if it does not exist.
     */
    ConversationResponse createConversation(Long senderId, Long receiverId);

    /**
     * Get conversation by id.
     */
    ConversationResponse getConversation(Long conversationId);

    /**
     * Get conversation between two users.
     */
    ConversationResponse getConversationBetweenUsers(Long senderId, Long receiverId);

    /**
     * Get all conversations of a user.
     */
    List<ConversationResponse> getUserConversations(Long userId);

    /**
     * Archive a conversation.
     */
    void archiveConversation(Long conversationId);

    /**
     * Unarchive a conversation.
     */
    void unarchiveConversation(Long conversationId);

    /**
     * Soft delete a conversation.
     */
    void deleteConversation(Long conversationId);

    /**
     * Restore a deleted conversation.
     */
    void restoreConversation(Long conversationId);

    /**
     * Check whether a conversation exists.
     */
    boolean exists(Long conversationId);

    /**
     * Get total conversations of a user.
     */
    long countUserConversations(Long userId);
}
