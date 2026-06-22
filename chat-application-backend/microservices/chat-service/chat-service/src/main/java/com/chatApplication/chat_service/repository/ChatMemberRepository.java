package com.chatApplication.chat_service.repository;

import com.chatApplication.chat_service.entity.ChatMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatMemberRepository extends JpaRepository<ChatMember,Long> {
    List<ChatMember> findByUserId(Long userId);
    List<ChatMember> findByChatId(Long chatId);
    boolean existsByChatIdAndUserId(
            Long chatId,
            Long userId
    );
    void deleteByChatId(
            Long chatId
    );
}
