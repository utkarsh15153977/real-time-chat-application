package com.chatApplication.message_service.repository;


import org.apache.logging.log4j.message.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MessageRepository extends JpaRepository<Message,Long> {
    List<Message> findBySenderIdAndReceiverId(String sender, String receiver);
}