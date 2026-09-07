package com.chatApplication.chat_service.controller;

import com.chatApplication.chat_service.dto.BulkPresenceResponse;
import com.chatApplication.chat_service.service.PresenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * REST controller for querying user presence status.
 * <p>
 * Provides two endpoints:
 * - GET /api/presence/{userId}: Check if a single user is online
 * - GET /api/presence/bulk?ids=1,2,3: Check online status for multiple users
 * <p>
 * These endpoints are called by the frontend (or other services) to populate
 * the online indicator next to user avatars in the chat list.
 */
@Slf4j
@RestController
@RequestMapping("/api/presence")
@RequiredArgsConstructor
public class PresenceRestController {

    private final PresenceService presenceService;

    /**
     * Check if a single user is currently online.
     *
     * @param userId the user ID to check
     * @return {"online": true/false}
     */
    @GetMapping("/{userId}")
    public ResponseEntity<Map<String, Boolean>> getUserPresence(
            @PathVariable Long userId) {

        boolean isOnline = presenceService.isUserOnline(userId);

        log.debug("Presence check for user {}: {}", userId, isOnline);

        return ResponseEntity.ok(Map.of("online", isOnline));
    }

    /**
     * Check online status for multiple users in a single request.
     * <p>
     * This is the preferred endpoint for populating a chat list with
     * online indicators, as it avoids N+1 query overhead.
     *
     * @param ids comma-separated list of user IDs (e.g., "1,2,3")
     * @return mapping of userId -> isOnline status
     */
    @GetMapping("/bulk")
    public ResponseEntity<BulkPresenceResponse> getBulkPresence(
            @RequestParam String ids) {

        // Parse comma-separated IDs into a list of Longs
        List<Long> userIds = Arrays.stream(ids.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Long::parseLong)
                .collect(Collectors.toList());

        Map<Long, Boolean> statuses =
                presenceService.getBulkPresenceStatus(userIds);

        log.debug("Bulk presence check for {} users: {}",
                userIds.size(), statuses);

        return ResponseEntity.ok(new BulkPresenceResponse(statuses));
    }
}
