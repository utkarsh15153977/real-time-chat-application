package com.chatApplication.message_service.controller;

import com.chatApplication.message_service.dto.DeviceTokenRequestDTO;
import com.chatApplication.message_service.service.PushNotificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for FCM device token registration management.
 * <p>
 * Security model:
 *   - All endpoints require valid X-User-Id header (injected by API Gateway)
 *   - Device tokens are scoped to the authenticated user
 * <p>
 * Endpoints:
 *   - POST /api/v1/devices: Register a device token
 *   - DELETE /api/v1/devices/{fcmToken}: Unregister a device token
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/devices")
@RequiredArgsConstructor
public class DeviceTokenController {

    private final PushNotificationService pushNotificationService;

    /**
     * Registers or updates an FCM device token for the authenticated user.
     *
     * @param userId  the authenticated user ID (from X-User-Id header)
     * @param request the device token registration request
     * @return 200 OK on success
     */
    @PostMapping
    public ResponseEntity<Void> registerDevice(
            @RequestHeader("X-User-Id") String userId,
            @Valid @RequestBody DeviceTokenRequestDTO request) {

        log.debug("POST /api/v1/devices - userId={}, deviceType={}",
                userId, request.getDeviceType());

        pushNotificationService.registerDeviceToken(userId, request);

        return ResponseEntity.ok().build();
    }

    /**
     * Unregisters a specific FCM device token for the authenticated user.
     *
     * @param userId   the authenticated user ID (from X-User-Id header)
     * @param fcmToken the FCM token to unregister (URL-encoded)
     * @return 200 OK on success
     */
    @DeleteMapping("/{fcmToken}")
    public ResponseEntity<Void> unregisterDevice(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable String fcmToken) {

        log.debug("DELETE /api/v1/devices/{} - userId={}",
                fcmToken.length() > 8
                        ? fcmToken.substring(0, 4) + "****" + fcmToken.substring(fcmToken.length() - 4)
                        : "****",
                userId);

        pushNotificationService.unregisterDeviceToken(userId, fcmToken);

        return ResponseEntity.ok().build();
    }
}
