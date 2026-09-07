package com.chatApplication.message_service.service;

import com.chatApplication.message_service.dto.ChatMessageResponseDTO;
import com.chatApplication.message_service.dto.InboxItemDTO;
import com.chatApplication.message_service.dto.TotalUnreadCountDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Service interface for inbox overview and unread message counter operations.
 * <p>
 * Provides high-performance REST endpoints and real-time STOMP updates
 * for user inbox summaries, including last message preview, timestamp,
 * recipient details, and unread message counters per conversation.
 */
public interface InboxService {

    /**
     * Fetches paginated inbox list for a user.
     * <p>
     * Each item contains:
     * - Chat room ID and conversation partner ID
     * - Last message content, type, and timestamp
     * - Unread message count for the conversation
     * <p>
     * Results are sorted by last message timestamp descending (newest first).
     *
     * @param userId   the user ID whose inbox to fetch
     * @param pageable pagination parameters (page, size, sort)
     * @return paginated list of inbox items
     */
    Page<InboxItemDTO> getInboxOverview(String userId, Pageable pageable);

    /**
     * Returns total count of unread messages across all conversations.
     * Used for badge display on the inbox icon.
     *
     * @param userId the user ID to count unread messages for
     * @return DTO containing the total unread count
     */
    TotalUnreadCountDTO getTotalUnreadCount(String userId);

    /**
     * Constructs and broadcasts an inbox update to the recipient's
     * personal inbox queue when a new message arrives.
     * <p>
     * Broadcasts to: /user/{recipientId}/queue/inbox
     *
     * @param responseDTO the newly saved message response DTO
     */
    void notifyInboxUpdate(ChatMessageResponseDTO responseDTO);
}
