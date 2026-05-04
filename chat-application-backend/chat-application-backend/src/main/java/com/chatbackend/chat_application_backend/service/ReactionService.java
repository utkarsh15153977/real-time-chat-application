package com.chatbackend.chat_application_backend.service;

import com.chatbackend.chat_application_backend.entity.Reaction;

import java.util.List;

public interface ReactionService {
    // Add reaction to a message
    Reaction addReaction(Long messageId, Long userId, String reactionType);
    // Remove reaction
    void removeReaction(Long messageId, Long userId);
    // Update reaction (change 👍 → ❤️)
    Reaction updateReaction(Long messageId, Long userId, String reactionType);
    // Get all reactions for a message
    List<Reaction> getReactionsByMessage(Long messageId);
    // Get reactions given by a user
    List<Reaction> getReactionsByUser(Long userId);
    // Count reactions for a message
    long countReactionsByMessage(Long messageId);
}