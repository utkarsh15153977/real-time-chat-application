package com.chatApplication.message_service.service;

import com.chatApplication.message_service.dto.*;
import com.chatApplication.message_service.entity.MessageType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface MessageService {

    // Legacy message sending (REST API)
    MessageResponse sendMessage(MessageRequest request);

    /** Persist a chat message from a ChatMessageRequestDTO (WebSocket or REST) */
    ChatMessageResponseDTO saveMessage(ChatMessageRequestDTO requestDTO);

    /** Retrieve paginated chat history for a room */
    Page<ChatMessageResponseDTO> getChatHistory(String chatRoomId, Pageable pageable);

    List<MessageResponse> getConversation(String user1, String user2);

    MessageResponse getMessage(Long messageId);

    MessageResponse markAsDelivered(Long messageId);

    MessageResponse markAsSeen(Long messageId);

    void markAllAsSeen(String senderId, String receiverId);

    void deleteMessage(Long messageId);

    MessageResponse editMessage(Long messageId, String content);

    Long getUnreadCount(String senderId, String receiverId);

    List<MessageResponse> getRecentMessages(String userId);

    boolean exists(Long messageId);

    MessageResponse sendAttachment(AttachmentRequest request);

    // ================================================================
    // Read Receipts & Dynamic Delivery Status (Phase 3)
    // ================================================================

    /**
     * Mark a single message as DELIVERED.
     * Only transitions from SENT -> DELIVERED (idempotent).
     *
     * @param messageId   the message ID to update
     * @param recipientId the user ID of the recipient (must match)
     * @return the status update DTO for broadcasting
     */
    MessageStatusUpdateDTO markAsDelivered(String messageId, String recipientId);

    /**
     * Mark a single message as READ with read_at timestamp.
     * Transitions from SENT or DELIVERED -> READ (idempotent).
     *
     * @param messageId   the message ID to update
     * @param recipientId the user ID of the recipient (must match)
     * @return the status update DTO for broadcasting
     */
    MessageStatusUpdateDTO markAsRead(String messageId, String recipientId);

    /**
     * Bulk mark all unread messages in a chat room as READ.
     * Used when a user opens a conversation.
     *
     * @param chatRoomId the chat room identifier (format: "{senderId}-{recipientId}")
     * @param readerId   the user ID of the reader
     * @return the bulk status update DTO for broadcasting
     */
    BulkStatusUpdateDTO markChatRoomAsRead(String chatRoomId, String readerId);
}
