package com.chatbackend.chat_application_backend.service;

import com.chatbackend.chat_application_backend.entity.Message;

import java.util.List;

public interface MessageService {
    // Send message
    Message sendMessage(Long senderId, String content, Long chatRoomId);
    // Get messages for a chat
    List<Message> getMessagesByChatRoom(Long chatRoomId);
    // Get single message
    Message getMessage(Long messageId);
    // Delete message
    void deleteMessage(Long messageId);
    // Mark message as delivered
    void markMessageDelivered(Long messageId);
    // Mark message as read
    void markMessageRead(Long messageId);
    // Edit message
    Message editMessage(Long messageId, String newContent);
    // Mark all messages in chat as delivered
    void markAllMessagesDelivered(Long chatRoomId, Long userId);
    // Mark all messages in chat as read
    void markAllMessagesRead(Long chatRoomId, Long userId);
    // Get unread message count
    long getUnreadMessageCount(Long userId, Long chatRoomId);
    // Search messages in chat
    List<Message> searchMessages(Long chatRoomId, String keyword);
}