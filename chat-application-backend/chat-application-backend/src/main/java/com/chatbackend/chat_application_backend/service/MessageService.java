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
    // Mark message as delivered (single message)
    void markMessageDelivered(Long messageId);
    // Mark message as read (single message)
    void markMessageRead(Long messageId);
    // Mark as delivered with ACK push
    void markAsDelivered(Long messageId, Long userId);
    // Mark all messages as read with ACK push
    void markAsRead(Long chatRoomId, Long userId);
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