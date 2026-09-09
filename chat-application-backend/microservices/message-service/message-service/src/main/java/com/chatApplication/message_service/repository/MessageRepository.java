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
import java.util.List;

@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {

    // ================================================================
    // Basic Message Queries
    // ================================================================

    /**
     * Finds messages sent from one user to another.
     */
    List<Message> findBySenderIdAndReceiverId(
            String senderId,
            String receiverId
    );

    /**
     * Finds the complete conversation between two users.
     * Results are returned from oldest to newest.
     */
    @Query("""
            SELECT m
            FROM Message m
            WHERE (m.senderId = :user1 AND m.receiverId = :user2)
               OR (m.senderId = :user2 AND m.receiverId = :user1)
            ORDER BY m.timestamp ASC
            """)
    List<Message> findConversation(
            @Param("user1") String user1,
            @Param("user2") String user2
    );

    // ================================================================
    // Unread Message Queries
    // ================================================================

    /**
     * Finds unread messages sent from senderId to receiverId.
     *
     * A message is considered unread when its status is either:
     * SENT or DELIVERED.
     *
     * READ and FAILED messages are not considered unread.
     */
    @Query("""
            SELECT m
            FROM Message m
            WHERE m.senderId = :senderId
              AND m.receiverId = :receiverId
              AND m.status IN (
                  com.chatApplication.message_service.entity.MessageStatus.SENT,
                  com.chatApplication.message_service.entity.MessageStatus.DELIVERED
              )
            ORDER BY m.timestamp ASC
            """)
    List<Message> findUnreadMessages(
            @Param("senderId") String senderId,
            @Param("receiverId") String receiverId
    );

    /**
     * Counts unread messages sent from senderId to receiverId.
     *
     * Unread statuses are SENT and DELIVERED.
     */
    @Query("""
            SELECT COUNT(m)
            FROM Message m
            WHERE m.senderId = :senderId
              AND m.receiverId = :receiverId
              AND m.status IN (
                  com.chatApplication.message_service.entity.MessageStatus.SENT,
                  com.chatApplication.message_service.entity.MessageStatus.DELIVERED
              )
            """)
    Long countUnreadMessages(
            @Param("senderId") String senderId,
            @Param("receiverId") String receiverId
    );

    /**
     * Finds all messages involving a user.
     * Results are returned from newest to oldest.
     */
    @Query("""
            SELECT m
            FROM Message m
            WHERE m.senderId = :userId
               OR m.receiverId = :userId
            ORDER BY m.timestamp DESC
            """)
    List<Message> findRecentMessages(
            @Param("userId") String userId
    );

    // ================================================================
    // Conversation Read Status
    // ================================================================

    /**
     * Finds unread messages in a specific conversation.
     *
     * Used by the READ_ACK flow to identify messages that
     * need to be marked as READ.
     */
    @Query("""
            SELECT m
            FROM Message m
            WHERE m.senderId = :senderId
              AND m.receiverId = :recipientId
              AND m.status IN (
                  com.chatApplication.message_service.entity.MessageStatus.SENT,
                  com.chatApplication.message_service.entity.MessageStatus.DELIVERED
              )
            ORDER BY m.timestamp ASC
            """)
    List<Message> findUnreadMessagesInConversation(
            @Param("senderId") String senderId,
            @Param("recipientId") String recipientId
    );

    // ================================================================
    // Pagination Queries
    // ================================================================

    /**
     * Finds paginated message history for a user.
     *
     * Returns messages where the user is either the sender or receiver.
     * Results are ordered newest first.
     */
    @Query("""
            SELECT m
            FROM Message m
            WHERE m.senderId = :userId
               OR m.receiverId = :userId
            ORDER BY m.timestamp DESC
            """)
    Page<Message> findByUserId(
            @Param("userId") String userId,
            Pageable pageable
    );

    /**
     * Finds paginated conversation history between two users.
     * Results are ordered newest first.
     */
    @Query("""
            SELECT m
            FROM Message m
            WHERE (m.senderId = :user1 AND m.receiverId = :user2)
               OR (m.senderId = :user2 AND m.receiverId = :user1)
            ORDER BY m.timestamp DESC
            """)
    Page<Message> findConversationPaged(
            @Param("user1") String user1,
            @Param("user2") String user2,
            Pageable pageable
    );

    // ================================================================
    // Message Type Queries
    // ================================================================

    /**
     * Finds messages sent by a user filtered by message type.
     */
    List<Message> findBySenderIdAndMessageType(
            String senderId,
            MessageType messageType
    );

    // ================================================================
    // Chat Room Read Status
    // ================================================================

    /**
     * Marks all unread messages in a chat room as the supplied status.
     *
     * Only messages received by readerId are updated.
     * Messages that are already READ are ignored.
     *
     * @param chatRoomId chat room identifier in the format:
     *                   "{senderId}-{receiverId}"
     * @param readerId   user who is reading the messages
     * @param status     new message status
     * @param readAt     timestamp at which messages were read
     * @return number of updated messages
     */
    @Modifying
    @Query("""
            UPDATE Message m
            SET m.status = :status,
                m.readAt = :readAt
            WHERE (m.senderId || '-' || m.receiverId) = :chatRoomId
              AND m.receiverId = :readerId
              AND m.status IN (
                  com.chatApplication.message_service.entity.MessageStatus.SENT,
                  com.chatApplication.message_service.entity.MessageStatus.DELIVERED
              )
            """)
    int markMessagesAsReadInChatRoom(
            @Param("chatRoomId") String chatRoomId,
            @Param("readerId") String readerId,
            @Param("status") MessageStatus status,
            @Param("readAt") Instant readAt
    );

    // ================================================================
    // Single Message Status Update
    // ================================================================

    /**
     * Updates the status of a single message.
     *
     * The recipientId check prevents unauthorized users from
     * modifying another user's message status.
     *
     * @param messageId   message ID
     * @param recipientId recipient who owns the message
     * @param status      new message status
     * @param readAt      read timestamp; normally set for READ
     * @return number of updated messages (0 or 1)
     */
    @Modifying
    @Query("""
            UPDATE Message m
            SET m.status = :status,
                m.readAt = :readAt
            WHERE m.msgId = :messageId
              AND m.receiverId = :recipientId
            """)
    int updateMessageStatus(
            @Param("messageId") Long messageId,
            @Param("recipientId") String recipientId,
            @Param("status") MessageStatus status,
            @Param("readAt") Instant readAt
    );

    /**
     * Finds unread messages in a chat room for a specific reader.
     *
     * Only SENT and DELIVERED messages are considered unread.
     */
    @Query("""
            SELECT m
            FROM Message m
            WHERE (m.senderId || '-' || m.receiverId) = :chatRoomId
              AND m.receiverId = :readerId
              AND m.status IN (
                  com.chatApplication.message_service.entity.MessageStatus.SENT,
                  com.chatApplication.message_service.entity.MessageStatus.DELIVERED
              )
            ORDER BY m.timestamp ASC
            """)
    List<Message> findUnreadMessagesInChatRoom(
            @Param("chatRoomId") String chatRoomId,
            @Param("readerId") String readerId
    );

    // ================================================================
    // Inbox Overview Queries
    // ================================================================

    /**
     * Counts all unread messages received by a user.
     *
     * Used for the inbox/unread badge.
     */
    @Query("""
            SELECT COUNT(m)
            FROM Message m
            WHERE m.receiverId = :userId
              AND m.status IN (
                  com.chatApplication.message_service.entity.MessageStatus.SENT,
                  com.chatApplication.message_service.entity.MessageStatus.DELIVERED
              )
            """)
    long countTotalUnreadMessages(
            @Param("userId") String userId
    );

    /**
     * Finds the latest message for every conversation involving a user.
     *
     * Uses the highest message ID for each conversation as the latest
     * message and orders the resulting conversations newest first.
     */
    @Query("""
            SELECT m
            FROM Message m
            WHERE m.msgId IN (
                SELECT MAX(m2.msgId)
                FROM Message m2
                WHERE m2.senderId = :userId
                   OR m2.receiverId = :userId
                GROUP BY
                    CASE
                        WHEN m2.senderId = :userId
                        THEN m2.receiverId
                        ELSE m2.senderId
                    END
            )
            ORDER BY m.timestamp DESC
            """)
    List<Message> findLatestMessagesPerConversation(
            @Param("userId") String userId
    );

    /**
     * Counts unread messages in a specific conversation.
     *
     * Counts only messages sent by partnerId to userId.
     */
    @Query("""
            SELECT COUNT(m)
            FROM Message m
            WHERE m.senderId = :partnerId
              AND m.receiverId = :userId
              AND m.status IN (
                  com.chatApplication.message_service.entity.MessageStatus.SENT,
                  com.chatApplication.message_service.entity.MessageStatus.DELIVERED
              )
            """)
    long countUnreadInConversation(
            @Param("userId") String userId,
            @Param("partnerId") String partnerId
    );

    // ================================================================
    // Latest Message Between Users
    // ================================================================

    /**
     * Finds messages between two users ordered newest first.
     *
     * Use Pageable with size = 1 when only the latest message is needed.
     */
    @Query("""
            SELECT m
            FROM Message m
            WHERE (m.senderId = :user1 AND m.receiverId = :user2)
               OR (m.senderId = :user2 AND m.receiverId = :user1)
            ORDER BY m.timestamp DESC, m.msgId DESC
            """)
    List<Message> findLastMessageBetweenUsers(
            @Param("user1") String user1,
            @Param("user2") String user2,
            Pageable pageable
    );
}