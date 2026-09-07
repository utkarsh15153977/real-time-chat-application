package com.chatApplication.message_service.service;

import com.chatApplication.message_service.dto.ChatMessageResponseDTO;
import com.chatApplication.message_service.dto.InboxItemDTO;
import com.chatApplication.message_service.dto.TotalUnreadCountDTO;
import com.chatApplication.message_service.entity.Message;
import com.chatApplication.message_service.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Implementation of inbox overview and unread message counter operations.
 * <p>
 * Performance considerations:
 * - Uses batch queries to avoid N+1 overhead
 * - Leverages database indexes for fast unread count calculations
 * - In-memory sorting for conversation partner grouping
 * - Read-only transactions for all query operations
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InboxServiceImpl implements InboxService {

    private final MessageRepository messageRepository;
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * {@inheritDoc}
     * <p>
     * Implementation strategy:
     * 1. Fetch all latest messages per conversation (single query)
     * 2. Group by conversation partner
     * 3. Calculate unread counts for each conversation
     * 4. Build InboxItemDTO list
     * 5. Apply pagination in-memory (optimized for typical inbox sizes)
     */
    @Override
    @Transactional(readOnly = true)
    public Page<InboxItemDTO> getInboxOverview(String userId, Pageable pageable) {
        log.debug("Fetching inbox overview for user: {}", userId);

        // Step 1: Fetch all messages for the user (single query)
        List<Message> allMessages = messageRepository.findLatestMessagesPerConversation(userId);

        // Step 2: Group messages by conversation partner and get latest per conversation
        Map<String, Message> latestByPartner = allMessages.stream()
                .collect(Collectors.toMap(
                        m -> getConversationPartner(m, userId),
                        m -> m,
                        (m1, m2) -> m1.getTimestamp().isAfter(m2.getTimestamp()) ? m1 : m2
                ));

        // Step 3: Build InboxItemDTO list with unread counts
        List<InboxItemDTO> inboxItems = new ArrayList<>();
        for (Map.Entry<String, Message> entry : latestByPartner.entrySet()) {
            String partnerId = entry.getKey();
            Message lastMessage = entry.getValue();

            // Count unread messages from this partner
            long unreadCount = messageRepository.countUnreadInConversation(userId, partnerId);

            // Generate chat room ID (consistent format: smaller ID first)
            String chatRoomId = generateChatRoomId(userId, partnerId);

            InboxItemDTO item = InboxItemDTO.builder()
                    .chatRoomId(chatRoomId)
                    .recipientId(partnerId)
                    .lastMessageContent(lastMessage.getContent())
                    .lastMessageType(lastMessage.getMessageType())
                    .lastMessageTimestamp(toInstant(lastMessage.getTimestamp()))
                    .lastMessageSenderId(lastMessage.getSenderId())
                    .unreadCount(unreadCount)
                    .build();

            inboxItems.add(item);
        }

        // Step 4: Sort by last message timestamp descending (newest first)
        inboxItems.sort(Comparator.comparing(InboxItemDTO::getLastMessageTimestamp).reversed());

        // Step 5: Apply pagination in-memory
        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), inboxItems.size());

        List<InboxItemDTO> paginatedItems = start < inboxItems.size()
                ? inboxItems.subList(start, end)
                : List.of();

        log.debug("Inbox overview: {} conversations found for user {}",
                inboxItems.size(), userId);

        return new PageImpl<>(paginatedItems, pageable, inboxItems.size());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public TotalUnreadCountDTO getTotalUnreadCount(String userId) {
        long totalUnread = messageRepository.countTotalUnreadMessages(userId);

        log.debug("Total unread count for user {}: {}", userId, totalUnread);

        return TotalUnreadCountDTO.builder()
                .totalUnreadCount(totalUnread)
                .build();
    }

    /**
     * {@inheritDoc}
     * <p>
     * Implementation:
     * 1. Extract sender and recipient from the response DTO
     * 2. Count unread messages for the recipient
     * 3. Build and broadcast InboxItemDTO
     */
    @Override
    @Transactional
    public void notifyInboxUpdate(ChatMessageResponseDTO responseDTO) {
        String recipientId = responseDTO.getRecipientId();
        String senderId = responseDTO.getSenderId();

        log.debug("Notifying inbox update for user {} from message {}",
                recipientId, responseDTO.getMessageId());

        try {
            // Count unread messages for the recipient from this sender
            long unreadCount = messageRepository.countUnreadInConversation(
                    recipientId, senderId);

            // Generate chat room ID
            String chatRoomId = generateChatRoomId(senderId, recipientId);

            // Build inbox update DTO using data from the response
            InboxItemDTO inboxUpdate = InboxItemDTO.builder()
                    .chatRoomId(chatRoomId)
                    .recipientId(senderId)
                    .lastMessageContent(responseDTO.getContent())
                    .lastMessageType(responseDTO.getMessageType())
                    .lastMessageTimestamp(responseDTO.getTimestamp())
                    .lastMessageSenderId(responseDTO.getSenderId())
                    .unreadCount(unreadCount)
                    .build();

            // Broadcast to recipient's personal inbox queue
            messagingTemplate.convertAndSendToUser(
                    recipientId,
                    "/queue/inbox",
                    inboxUpdate);

            log.debug("Inbox update broadcast to user {}: room={}, unread={}",
                    recipientId, chatRoomId, unreadCount);

        } catch (Exception e) {
            log.error("Failed to notify inbox update for user {}: {}",
                    recipientId, e.getMessage());
        }
    }

    /**
     * Extracts the conversation partner ID from a message.
     * If the user is the sender, the partner is the receiver, and vice versa.
     *
     * @param message the message
     * @param userId  the current user ID
     * @return the conversation partner's user ID
     */
    private String getConversationPartner(Message message, String userId) {
        if (message.getSenderId().equals(userId)) {
            return message.getReceiverId();
        }
        return message.getSenderId();
    }

    /**
     * Generates a consistent chat room ID from two user IDs.
     * The smaller ID (lexicographically) comes first to ensure
     * the same room ID regardless of who initiated the conversation.
     *
     * @param userId1 first user ID
     * @param userId2 second user ID
     * @return consistent chat room ID in format "{smaller}-{larger}"
     */
    private String generateChatRoomId(String userId1, String userId2) {
        if (userId1.compareTo(userId2) < 0) {
            return userId1 + "-" + userId2;
        }
        return userId2 + "-" + userId1;
    }

    /**
     * Converts LocalDateTime to Instant for DTO serialization.
     *
     * @param localDateTime the LocalDateTime to convert
     * @return the corresponding Instant (UTC)
     */
    private Instant toInstant(java.time.LocalDateTime localDateTime) {
        if (localDateTime == null) {
            return Instant.now();
        }
        return localDateTime.toInstant(java.time.ZoneOffset.UTC);
    }
}
