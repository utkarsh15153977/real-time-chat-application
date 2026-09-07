package com.chatapplication.group_chat.repository;

import com.chatapplication.group_chat.entitty.Conversation;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.awt.print.Pageable;
import java.util.List;
import java.util.Optional;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {
    /**
     * Find conversation between two users.
     */
    @Query("""
            SELECT c
            FROM Conversation c
            WHERE
            ((c.user1Id = :user1Id AND c.user2Id = :user2Id)
            OR
            (c.user1Id = :user2Id AND c.user2Id = :user1Id))
            AND c.deleted = false
            """)
    Optional<Conversation> findConversation(
            @Param("user1Id") Long user1Id,
            @Param("user2Id") Long user2Id
    );

    /**
     * Get all conversations of a user.
     */
    @Query("""
            SELECT c
            FROM Conversation c
            WHERE
            (c.user1Id = :userId
            OR c.user2Id = :userId)
            AND c.deleted = false
            ORDER BY c.updatedAt DESC
            """)
    List<Conversation> findUserConversations(
            @Param("userId") Long userId
    );

    /**
     * Paginated conversation list.
     */
    @Query("""
            SELECT c
            FROM Conversation c
            WHERE
            (c.user1Id = :userId
            OR c.user2Id = :userId)
            AND c.deleted = false
            ORDER BY c.updatedAt DESC
            """)
    List<Conversation> findUserConversations(
            @Param("userId") Long userId,
            Pageable pageable
    );

    /**
     * Find latest conversation.
     */
    @Query("""
            SELECT c
            FROM Conversation c
            WHERE
            (c.user1Id = :userId
            OR c.user2Id = :userId)
            AND c.deleted = false
            ORDER BY c.updatedAt DESC
            """)
    List<Conversation> findRecentConversations(
            @Param("userId") Long userId,
            Pageable pageable
    );

    /**
     * Search conversations by participant.
     */
    @Query("""
            SELECT c
            FROM Conversation c
            WHERE
            (c.user1Id = :userId
            OR c.user2Id = :userId)
            AND c.deleted = false
            """)
    List<Conversation> searchConversation(
            @Param("userId") Long userId
    );

    /**
     * Conversation exists.
     */
    boolean existsByUser1IdAndUser2Id(Long user1Id, Long user2Id);

    /**
     * Count conversations of a user.
     */
    @Query("""
            SELECT COUNT(c)
            FROM Conversation c
            WHERE
            (c.user1Id = :userId
            OR c.user2Id = :userId)
            AND c.deleted = false
            """)
    long countUserConversations(
            @Param("userId") Long userId
    );

    /**
     * Conversations having unread messages for User1.
     */
    List<Conversation> findByUser1IdAndUser1UnreadCountGreaterThanAndDeletedFalse(
            Long user1Id,
            Integer unreadCount
    );

    /**
     * Conversations having unread messages for User2.
     */
    List<Conversation> findByUser2IdAndUser2UnreadCountGreaterThanAndDeletedFalse(
            Long user2Id,
            Integer unreadCount
    );

    /**
     * Soft delete conversation.
     */
    @Modifying
    @Query("""
            UPDATE Conversation c
            SET c.deleted = true
            WHERE c.id = :conversationId
            """)
    void softDelete(
            @Param("conversationId") Long conversationId
    );

    /**
     * Archive conversation.
     */
    @Modifying
    @Query("""
            UPDATE Conversation c
            SET c.archived = true
            WHERE c.id = :conversationId
            """)
    void archiveConversation(
            @Param("conversationId") Long conversationId
    );
    List<Conversation> findBySenderIdOrReceiverId(
            Long senderId,
            Long receiverId);

    
}
