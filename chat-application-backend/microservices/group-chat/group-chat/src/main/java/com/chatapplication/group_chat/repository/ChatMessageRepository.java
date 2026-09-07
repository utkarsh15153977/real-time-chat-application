package com.chatapplication.group_chat.repository;

import com.chatapplication.group_chat.entitty.ChatMessage;
import com.chatapplication.group_chat.entitty.Conversation;
import com.chatapplication.group_chat.entitty.MessageStatus;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.awt.print.Pageable;
import java.util.List;
import java.util.Optional;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    /**
     * Get complete conversation
     */
    List<ChatMessage> findByConversationOrderByCreatedAtAsc(
            Conversation conversation
    );

    /**
     * Paginated conversation
     */
    List<ChatMessage> findByConversationOrderByCreatedAtDesc(
            Conversation conversation,
            Pageable pageable
    );

    /**
     * Recent message of conversation
     */
    Optional<ChatMessage> findTopByConversationOrderByCreatedAtDesc(
            Conversation conversation
    );

    /**
     * Unread messages
     */
    List<ChatMessage> findByConversationAndReceiverIdAndStatus(
            Conversation conversation,
            Long receiverId,
            MessageStatus status
    );

    /**
     * Count unread messages
     */
    long countByConversationAndReceiverIdAndStatus(
            Conversation conversation,
            Long receiverId,
            MessageStatus status
    );

    /**
     * Messages sent by user
     */
    List<ChatMessage> findBySenderIdOrderByCreatedAtDesc(
            Long senderId
    );

    /**
     * Messages received by user
     */
    List<ChatMessage> findByReceiverIdOrderByCreatedAtDesc(
            Long receiverId
    );

    /**
     * Search messages in conversation
     */
    @Query("""
            SELECT m
            FROM ChatMessage m
            WHERE m.conversation = :conversation
            AND LOWER(m.content)
            LIKE LOWER(CONCAT('%', :keyword, '%'))
            ORDER BY m.createdAt ASC
            """)
    List<ChatMessage> searchMessages(
            @Param("conversation") Conversation conversation,
            @Param("keyword") String keyword
    );

    /**
     * Messages with attachment
     */
    @Query("""
            SELECT m
            FROM ChatMessage m
            WHERE m.attachment IS NOT NULL
            """)
    List<ChatMessage> findMessagesWithAttachment();

    /**
     * Soft delete
     */
    @Modifying
    @Query("""
            UPDATE ChatMessage m
            SET m.deleted = true
            WHERE m.id = :messageId
            """)
    void softDelete(
            @Param("messageId") Long messageId
    );

    /**
     * Mark Delivered
     */
    @Modifying
    @Query("""
            UPDATE ChatMessage m
            SET m.status = 'DELIVERED',
                m.deliveredAt = CURRENT_TIMESTAMP
            WHERE m.id = :messageId
            """)
    void markDelivered(
            @Param("messageId") Long messageId
    );

    /**
     * Mark Seen
     */
    @Modifying
    @Query("""
            UPDATE ChatMessage m
            SET m.status = 'SEEN',
                m.seenAt = CURRENT_TIMESTAMP
            WHERE m.id = :messageId
            """)
    void markSeen(
            @Param("messageId") Long messageId
    );

    /**
     * Delete all messages of conversation
     */
    @Modifying
    void deleteByConversation(
            Conversation conversation
    );

    /**
     * Total messages in conversation
     */
    long countByConversation(
            Conversation conversation
    );

    /**
     * Exists
     */
    boolean existsByIdAndDeletedFalse(
            Long id
    );

    List<ChatMessage> findConversation(
            Long senderId,
            Long receiverId);

    List<ChatMessage> findUnreadMessages(
            Long senderId,
            Long receiverId);

    Long countUnreadMessages(
            Long senderId,
            Long receiverId);
}
