package com.chatApplication.message_service.controller;

import com.chatApplication.message_service.dto.InboxItemDTO;
import com.chatApplication.message_service.dto.TotalUnreadCountDTO;
import com.chatApplication.message_service.service.InboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for inbox overview and unread message counter endpoints.
 * <p>
 * Security model:
 *   - All endpoints require valid X-User-Id header (injected by API Gateway)
 *   - The X-User-Id header is trusted because it's set by the Gateway
 *     after validating the JWT token
 * <p>
 * Endpoints:
 *   - GET /api/v1/inbox: Paginated inbox overview with last message preview
 *   - GET /api/v1/inbox/unread-count: Total unread message count
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/inbox")
@RequiredArgsConstructor
public class InboxController {

    private final InboxService inboxService;

    /**
     * Retrieves paginated inbox overview for the authenticated user.
     * <p>
     * Each item contains:
     * - Chat room ID and conversation partner ID
     * - Last message content, type, and timestamp
     * - Unread message count for the conversation
     * <p>
     * Default pagination: page=0, size=20, sort by lastMessageTimestamp DESC
     *
     * @param userId the authenticated user ID (from X-User-Id header)
     * @param page   page number (0-indexed, default 0)
     * @param size   page size (default 20)
     * @return paginated list of inbox items
     */
    @GetMapping
    public ResponseEntity<Page<InboxItemDTO>> getInboxOverview(
            @RequestHeader("X-User-Id") String userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        log.debug("GET /api/v1/inbox - userId={}, page={}, size={}", userId, page, size);

        // Validate page and size parameters
        page = Math.max(0, page);
        size = Math.min(Math.max(1, size), 100); // Limit max page size to 100

        // Create pageable with default sort by timestamp descending
        Pageable pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "lastMessageTimestamp"));

        Page<InboxItemDTO> inbox = inboxService.getInboxOverview(userId, pageable);

        return ResponseEntity.ok(inbox);
    }

    /**
     * Retrieves total unread message count for the authenticated user.
     * <p>
     * Used for displaying a badge on the inbox icon in the UI.
     *
     * @param userId the authenticated user ID (from X-User-Id header)
     * @return total unread count
     */
    @GetMapping("/unread-count")
    public ResponseEntity<TotalUnreadCountDTO> getTotalUnreadCount(
            @RequestHeader("X-User-Id") String userId) {

        log.debug("GET /api/v1/inbox/unread-count - userId={}", userId);

        TotalUnreadCountDTO unreadCount = inboxService.getTotalUnreadCount(userId);

        return ResponseEntity.ok(unreadCount);
    }
}
