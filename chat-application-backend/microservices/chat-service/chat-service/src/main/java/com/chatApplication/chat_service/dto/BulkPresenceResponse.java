package com.chatApplication.chat_service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Response DTO for bulk presence status queries.
 * <p>
 * Returns a map of userId -> online status (true/false)
 * so the client can render the online indicator for multiple
 * users in a chat list without separate API calls.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BulkPresenceResponse {
    /** userId -> isOnline mapping */
    private Map<Long, Boolean> statuses;
}
