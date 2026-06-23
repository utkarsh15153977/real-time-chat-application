package com.chatApplication.message_service.repository;

import com.chatApplication.message_service.entity.GroupMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GroupMessageRepository extends JpaRepository<GroupMessage, Long> {
    List<GroupMessage> findByGroupIdOrderBySentAtAsc(
            Long groupId);
}
