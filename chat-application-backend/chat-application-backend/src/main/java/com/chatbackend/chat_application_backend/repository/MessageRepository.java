package com.chatbackend.chat_application_backend.repository;

import com.chatbackend.chat_application_backend.entity.Message;
import com.chatbackend.chat_application_backend.entity.MessageStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MessageRepository extends JpaRepository<Message, Long> {
    @EntityGraph(attributePaths = {"sender", "chatRoom"})
    List<Message> findByChatRoom_IdOrderByTimestampAsc(Long chatRoomId);
    List<Message> findBySender_Id(Long senderId);
    @Query("""
            SELECT COUNT(m) FROM Message m
            WHERE m.chatRoom.id = :chatRoomId
            AND m.sender.id <> :senderId
            AND m.status <> :status
            """)
    long countByChatRoom_IdAndSender_IdNotAndStatus(@Param("chatRoomId") Long chatRoomId,
                                                    @Param("senderId") Long senderId,
                                                    @Param("status") MessageStatus status);
    List<Message> findByChatRoom_IdAndContentContainingIgnoreCase(Long chatRoomId,
                                                                  String keyword);
}
