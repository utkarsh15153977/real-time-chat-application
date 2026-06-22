package com.chatApplication.message_service.service;

import com.chatApplication.message_service.dto.MessageRequest;
import com.chatApplication.message_service.dto.MessageResponse;

import java.util.List;

public interface MessageService {
    MessageResponse sendMessage(MessageRequest request);
    List<MessageResponse> getConversation(
            String user1,
            String user2);

    MessageResponse getMessage(Long messageId);

    MessageResponse markAsDelivered(Long messageId);

    MessageResponse markAsSeen(Long messageId);

    // Mark All Messages as Seen
    void markAllAsSeen(
            String senderId,
            String receiverId);

    // Delete Message
    void deleteMessage(Long messageId);

    // Edit Message
    MessageResponse editMessage(
            Long messageId,
            String content);


    // Unread Count
    Long getUnreadCount(
            String senderId,
            String receiverId);

    // Recent Chats
    List<MessageResponse> getRecentMessages(
            String userId);

    // Message Existence
    boolean exists(Long messageId);

    // File/Image Messages
    MessageResponse sendAttachment(
            AttachmentRequest request);
}
