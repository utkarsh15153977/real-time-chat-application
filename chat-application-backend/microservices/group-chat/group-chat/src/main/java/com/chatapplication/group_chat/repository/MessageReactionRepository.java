package com.chatapplication.group_chat.repository;

import com.chatapplication.group_chat.entitty.ChatMessage;
import com.chatapplication.group_chat.entitty.GroupMessage;
import com.chatapplication.group_chat.entitty.MessageReaction;
import com.chatapplication.group_chat.entitty.ReactionType;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MessageReactionRepository  extends JpaRepository<MessageReaction, Long> {
    /**
     * Find all reactions of a private message
     */
    List<MessageReaction> findByChatMessage(
            ChatMessage chatMessage
    );

    /**
     * Find all reactions of a group message
     */
    List<MessageReaction> findByGroupMessage(
            GroupMessage groupMessage
    );

    /**
     * Find user's reaction on private message
     */
    Optional<MessageReaction> findByChatMessageAndUserId(
            ChatMessage chatMessage,
            Long userId
    );

    /**
     * Find user's reaction on group message
     */
    Optional<MessageReaction> findByGroupMessageAndUserId(
            GroupMessage groupMessage,
            Long userId
    );

    /**
     * Count reactions of a private message
     */
    long countByChatMessage(
            ChatMessage chatMessage
    );

    /**
     * Count reactions of a group message
     */
    long countByGroupMessage(
            GroupMessage groupMessage
    );

    /**
     * Count reactions by type on private message
     */
    long countByChatMessageAndReactionType(
            ChatMessage chatMessage,
            ReactionType reactionType
    );

    /**
     * Count reactions by type on group message
     */
    long countByGroupMessageAndReactionType(
            GroupMessage groupMessage,
            ReactionType reactionType
    );

    /**
     * Find all reactions by type
     */
    List<MessageReaction> findByReactionType(
            ReactionType reactionType
    );

    /**
     * Check whether user reacted on private message
     */
    boolean existsByChatMessageAndUserId(
            ChatMessage chatMessage,
            Long userId
    );

    /**
     * Check whether user reacted on group message
     */
    boolean existsByGroupMessageAndUserId(
            GroupMessage groupMessage,
            Long userId
    );

    /**
     * Delete reaction from private message
     */
    @Transactional
    void deleteByChatMessageAndUserId(
            ChatMessage chatMessage,
            Long userId
    );

    /**
     * Delete reaction from group message
     */
    @Transactional
    void deleteByGroupMessageAndUserId(
            GroupMessage groupMessage,
            Long userId
    );

    /**
     * Delete all reactions of a private message
     */
    @Transactional
    void deleteByChatMessage(
            ChatMessage chatMessage
    );

    /**
     * Delete all reactions of a group message
     */
    @Transactional
    void deleteByGroupMessage(
            GroupMessage groupMessage
    );

    /**
     * Find all reactions given by a user
     */
    List<MessageReaction> findByUserIdOrderByCreatedAtDesc(
            Long userId
    );

    /**
     * Count all reactions given by a user
     */
    long countByUserId(
            Long userId
    );

}
