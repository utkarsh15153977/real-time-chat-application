package com.chatApplication.message_service.repository;

import com.chatApplication.message_service.entity.Message;
import com.chatApplication.message_service.entity.MessageStatus;
import com.chatApplication.message_service.entity.MessageType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {

    List<Message> findBySenderIdAndReceiverId(
            String senderId,
            String receiverId);

    @Query("""
            SELECT m
            FROM Message m
            WHERE
            (m.senderId = :user1 AND m.receiverId = :user2)
            OR
            (m.senderId = :user2 AND m.receiverId = :user1)
            ORDER BY m.timestamp ASC
            """)
    List<Message> findConversation(
            @Param("user1") String user1,
            @Param("user2") String user2);

    @Query("""
            SELECT m
            FROM Message m
            WHERE m.senderId = :senderId
            AND m.receiverId = :receiverId
            AND m.status <> 'SEEN'
            """)
    List<Message> findUnreadMessages(
            @Param("senderId") String senderId,
            @Param("receiverId") String receiverId);

    @Query("""
            SELECT COUNT(m)
            FROM Message m
            WHERE m.senderId = :senderId
            AND m.receiverId = :receiverId
            AND m.status <> 'SEEN'
            """)
    Long countUnreadMessages(
            @Param("senderId") String senderId,
            @Param("receiverId") String receiverId);

    @Query("""
            SELECT m
            FROM Message m
            WHERE
            m.senderId = :userId
            OR
            m.receiverId = :userId
            ORDER BY m.timestamp DESC
            """)
    List<Message> findRecentMessages(
            @Param("userId") String userId);

    /**
     * Find unread messages in a conversation where the sender is senderId
     * and receiver is recipientId, and the status is not yet READ.
     * Used by the READ_ACK handler to bulk-mark messages as read.
     */
    @Query("""
            SELECT m
            FROM Message m
            WHERE m.senderId = :senderId
            AND m.receiverId = :recipientId
            AND m.status <> com.chatApplication.message_service.entity.MessageStatus.READ
            ORDER BY m.timestamp ASC
            """)
    List<Message> findUnreadMessagesInConversation(
            @Param("senderId") String senderId,
            @Param("recipientId") String recipientId);

    /**
     * Paginated query for chat room history.
     * Finds all messages where the user is either sender or receiver,
     * ordered by timestamp descending (newest first for pagination).
     */
    @Query("""
            SELECT m FROM Message m
            WHERE (m.senderId = :userId OR m.receiverId = :userId)
            ORDER BY m.timestamp DESC
            """)
    Page<Message> findByUserId(@Param("userId") String userId, Pageable pageable);

    /**
     * Paginated query for chat history between two specific users.
     */
    @Query("""
            SELECT m FROM Message m
            WHERE (m.senderId = :user1 AND m.receiverId = :user2)
               OR (m.senderId = :user2 AND m.receiverId = :user1)
            ORDER BY m.timestamp DESC
            """)
    Page<Message> findConversationPaged(
            @Param("user1") String user1,
            @Param("user2") String user2,
            Pageable pageable);

    /**
     * Find messages by message type for a specific user.
     */
    List<Message> findBySenderIdAndMessageType(
            String senderId,
            com.chatApplication.message_service.entity.MessageType messageType);

    /**
     * Bulk update all unread messages in a chat room to a new status.
     * Only updates messages where the recipient is the specified readerId
     * and the current status is not already READ.
     *
     * @param chatRoomId the chat room identifier (format: "{senderId}-{recipientId}")
     * @param readerId   the user ID of the reader
     * @param status     the new status to set
     * @param readAt     the timestamp when the message was read
     * @return the number of messages updated
     */
    @Modifying
    @Query("""
            UPDATE Message m
            SET m.status = :status, m.readAt = :readAt
            WHERE (m.senderId || '-' || m.receiverId) = :chatRoomId
            AND m.receiverId = :readerId
            AND m.status <> com.chatApplication.message_service.entity.MessageStatus.READ
            """)
    int markMessagesAsReadInChatRoom(
            @Param("chatRoomId") String chatRoomId,
            @Param("readerId") String readerId,
            @Param("status") MessageStatus status,
            @Param("readAt") Instant readAt);

    /**
     * Update a single message's status safely.
     * Only updates if the recipientId matches (prevents unauthorized status manipulation).
     *
     * @param messageId   the message ID to update
     * @param recipientId the user ID of the recipient (must match)
     * @param status      the new status to set
     * @param readAt      the timestamp (set for READ, null for DELIVERED)
     * @return the number of messages updated (0 or 1)
     */
    @Modifying
    @Query("""
            UPDATE Message m
            SET m.status = :status, m.readAt = :readAt
            WHERE m.msgId = :messageId
            AND m.receiverId = :recipientId
            """)
    int updateMessageStatus(
            @Param("messageId") Long messageId,
            @Param("recipientId") String recipientId,
            @Param("status") MessageStatus status,
            @Param("readAt") Instant readAt);

    /**
     * Find all messages in a chat room where the recipient is the reader
     * and status is not READ. Used to get message IDs for bulk updates.
     */
    @Query("""
            SELECT m FROM Message m
            WHERE (m.senderId || '-' || m.receiverId) = :chatRoomId
            AND m.receiverId = :readerId
            AND m.status <> com.chatApplication.message_service.entity.MessageStatus.READ
            ORDER BY m.timestamp ASC
            """)
    List<Message> findUnreadMessagesInChatRoom(
            @Param("chatRoomId") String chatRoomId,
            @Param("readerId") String readerId);

    // ================================================================
    // Inbox Overview Queries (Phase 4)
    // ================================================================

    /**
     * Count total unread messages for a user across all conversations.
     * Used for badge display on the inbox icon.
     *
     * @param userId the user ID to count unread messages for
     * @return total number of unread messages
     */
    @Query("""
            SELECT COUNT(m)
            FROM Message m
            WHERE m.receiverId = :userId
            AND m.status <> com.chatApplication.message_service.entity.MessageStatus.READ
            """)
    long countTotalUnreadMessages(@Param("userId") String userId);

    /**
     * Find the latest message for each conversation where the user is a participant.
     * Returns results ordered by timestamp descending for inbox display.
     * <p>
     * This query uses a subquery approach to avoid N+1 overhead:
     * - Finds all distinct conversation partners
     * - Retrieves the latest message for each conversation
     * <p>
     * Note: For high-performance production use, consider a native query with
     * window functions. This JPQL version is optimized for correctness.
     *
     * @param userId the user ID whose inbox to fetch
     * @return list of latest messages per conversation (newest first)
     */
    @Query("""
            SELECT m FROM Message m
            WHERE m.msgId IN (
                SELECT MAX(m2.msgId) FROM Message m2
                WHERE m2.senderId = :userId OR m2.receiverId = :userId
                GROUP BY
                    CASE
                        WHEN m2.senderId = :userId THEN m2.receiverId
                        ELSE m2.senderId
                    END
            )
            ORDER BY m.timestamp DESC
            """)
    List<Message> findLatestMessagesPerConversation(@Param("userId") String userId);

    /**
     * Count unread messages in a specific conversation for a user.
     *
     * @param userId    the user ID
     * @param partnerId the conversation partner's user ID
     * @return number of unread messages from partnerId to userId
     */
    @Query("""
            SELECT COUNT(m)
            FROM Message m
            WHERE m.senderId = :partnerId
            AND m.receiverId = :userId
            AND m.status <> com.chatApplication.message_service.entity.MessageStatus.READ
            """)
    long countUnreadInConversation(
            @Param("userId") String userId,
            @Param("partnerId") String partnerId);

    /**
     * Find the last message between two specific users.
     * Used for real-time inbox updates when a new message arrives.
     *
     * @param user1 first user ID
     * @param user2 second user ID
     * @return the most recent message between the two users, or null
     */
    @Query("""
            SELECT m FROM Message m
            WHERE (m.senderId = :user1 AND m.receiverId = :user2)
               OR (m.senderId = :user2 AND m.receiverId = :user1)
            ORDER BY m.timestamp DESC
            """)
    List<Message> findLastMessageBetweenUsers(
            @Param("user1") String user1,
            @Param("user2") String user2,
            Pageable pageable);
}
