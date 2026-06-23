package com.chatApplication.message_service.repository;

import com.chatApplication.message_service.entity.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

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
}