package com.chatApplication.chat_service.repository;

import com.chatApplication.chat_service.entity.Chat;
import com.chatApplication.chat_service.entity.ChatMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ChatRepository extends JpaRepository<Chat, Long> {

    @Query("SELECT c FROM Chat c WHERE c.isGroup = false AND c.active = true " +
           "AND c.id IN (SELECT cm.chatId FROM ChatMember cm WHERE cm.userId = :userId1) " +
           "AND c.id IN (SELECT cm.chatId FROM ChatMember cm WHERE cm.userId = :userId2)")
    Optional<Chat> findPrivateChatBetween(
            @Param("userId1") Long userId1,
            @Param("userId2") Long userId2);
}
