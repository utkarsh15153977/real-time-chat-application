package com.chatApplication.chat_service.repository;

import com.chatApplication.chat_service.entity.Chat;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatRepository extends JpaRepository<Chat,Long> {

}
