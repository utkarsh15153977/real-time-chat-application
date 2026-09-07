package com.chatApplication.message_service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO representing the total unread message count across all conversations.
 * <p>
 * Used for displaying a badge on the inbox icon in the UI.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TotalUnreadCountDTO {

    /** Total number of unread messages across all conversations */
    private long totalUnreadCount;
}
