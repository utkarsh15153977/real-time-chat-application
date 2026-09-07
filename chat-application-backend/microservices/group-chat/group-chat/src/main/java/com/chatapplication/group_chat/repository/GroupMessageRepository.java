package com.chatapplication.group_chat.repository;

import com.chatapplication.group_chat.entitty.Group;
import com.chatapplication.group_chat.entitty.GroupMessage;
import com.chatapplication.group_chat.entitty.MessageStatus;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.awt.print.Pageable;
import java.util.List;
import java.util.Optional;

public interface GroupMessageRepository extends JpaRepository<GroupMessage, Long> {
    /**
     * Get all messages of a group
     */
    List<GroupMessage> findByGroupOrderByCreatedAtAsc(
            Group group
    );

    /**
     * Paginated group messages
     */
    List<GroupMessage> findByGroupOrderByCreatedAtDesc(
            Group group,
            Pageable pageable
    );

    /**
     * Latest message
     */
    Optional<GroupMessage> findTopByGroupOrderByCreatedAtDesc(
            Group group
    );

    /**
     * Messages sent by a user in a group
     */
    List<GroupMessage> findByGroupAndSenderIdOrderByCreatedAtDesc(
            Group group,
            Long senderId
    );

    /**
     * Count messages in group
     */
    long countByGroup(
            Group group
    );

    /**
     * Search messages
     */
    @Query("""
            SELECT gm
            FROM GroupMessage gm
            WHERE gm.group = :group
            AND LOWER(gm.content)
            LIKE LOWER(CONCAT('%', :keyword, '%'))
            AND gm.deleted = false
            ORDER BY gm.createdAt ASC
            """)
    List<GroupMessage> searchMessages(
            @Param("group") Group group,
            @Param("keyword") String keyword
    );

    /**
     * Messages with attachments
     */
    @Query("""
            SELECT gm
            FROM GroupMessage gm
            WHERE gm.group = :group
            AND gm.attachment IS NOT NULL
            AND gm.deleted = false
            ORDER BY gm.createdAt DESC
            """)
    List<GroupMessage> findMessagesWithAttachment(
            @Param("group") Group group
    );

    /**
     * Get messages by status
     */
    List<GroupMessage> findByGroupAndStatus(
            Group group,
            MessageStatus status
    );

    /**
     * Soft delete message
     */
    @Modifying
    @Query("""
            UPDATE GroupMessage gm
            SET gm.deleted = true
            WHERE gm.id = :messageId
            """)
    int softDelete(
            @Param("messageId") Long messageId
    );

    /**
     * Mark delivered
     */
    @Modifying
    @Query("""
            UPDATE GroupMessage gm
            SET gm.status = 'DELIVERED',
                gm.deliveredAt = CURRENT_TIMESTAMP
            WHERE gm.id = :messageId
            """)
    int markDelivered(
            @Param("messageId") Long messageId
    );

    /**
     * Mark seen
     */
    @Modifying
    @Query("""
            UPDATE GroupMessage gm
            SET gm.status = 'SEEN',
                gm.seenAt = CURRENT_TIMESTAMP
            WHERE gm.id = :messageId
            """)
    int markSeen(
            @Param("messageId") Long messageId
    );

    /**
     * Delete all messages of a group
     */
    @Modifying
    void deleteByGroup(
            Group group
    );

    /**
     * Check message exists
     */
    boolean existsByIdAndDeletedFalse(
            Long id
    );

    /**
     * Count messages by sender
     */
    long countByGroupAndSenderId(
            Group group,
            Long senderId
    );

    /**
     * Find all replies to a message
     */
    List<GroupMessage> findByReplyToOrderByCreatedAtAsc(
            GroupMessage replyTo
    );

    /**
     * Find forwarded messages
     */
    List<GroupMessage> findByGroupAndForwardedTrue(
            Group group
    );

    /**
     * Find edited messages
     */
    List<GroupMessage> findByGroupAndEditedTrue(
            Group group
    );
}
