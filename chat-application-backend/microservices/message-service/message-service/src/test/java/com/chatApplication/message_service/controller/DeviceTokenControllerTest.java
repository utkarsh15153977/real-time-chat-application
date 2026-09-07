package com.chatApplication.message_service.controller;

import com.chatApplication.message_service.dto.DeviceTokenRequestDTO;
import com.chatApplication.message_service.entity.DeviceType;
import com.chatApplication.message_service.service.PushNotificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for DeviceTokenController REST endpoints.
 * <p>
 * Tests:
 * - POST /api/v1/devices with valid X-User-Id header
 * - DELETE /api/v1/devices/{fcmToken}
 * - Missing X-User-Id header handling
 * - Invalid request body handling
 */
@WebMvcTest(DeviceTokenController.class)
@AutoConfigureMockMvc(addFilters = false)
class DeviceTokenControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PushNotificationService pushNotificationService;

    @Autowired
    private ObjectMapper objectMapper;

    private DeviceTokenRequestDTO androidRequest;
    private DeviceTokenRequestDTO iosRequest;
    private DeviceTokenRequestDTO invalidRequest;

    @BeforeEach
    void setUp() {
        androidRequest = DeviceTokenRequestDTO.builder()
                .fcmToken("fcm-token-android-12345")
                .deviceType(DeviceType.ANDROID)
                .build();

        iosRequest = DeviceTokenRequestDTO.builder()
                .fcmToken("fcm-token-ios-67890")
                .deviceType(DeviceType.IOS)
                .build();

        invalidRequest = DeviceTokenRequestDTO.builder()
                .fcmToken(null)
                .deviceType(null)
                .build();
    }

    // ================================================================
    // POST /api/v1/devices tests
    // ================================================================

    @Nested
    @DisplayName("POST /api/v1/devices")
    class RegisterDeviceTests {

        @Test
        @DisplayName("should register Android device with valid request")
        void registerDevice_validAndroidRequest_returns200() throws Exception {
            // Act & Assert
            mockMvc.perform(post("/api/v1/devices")
                    .header("X-User-Id", "user1")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(androidRequest)))
                    .andExpect(status().isOk());

            verify(pushNotificationService).registerDeviceToken(
                    eq("user1"), any(DeviceTokenRequestDTO.class));
        }

        @Test
        @DisplayName("should register iOS device with valid request")
        void registerDevice_validIosRequest_returns200() throws Exception {
            // Act & Assert
            mockMvc.perform(post("/api/v1/devices")
                    .header("X-User-Id", "user1")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(iosRequest)))
                    .andExpect(status().isOk());

            verify(pushNotificationService).registerDeviceToken(
                    eq("user1"), any(DeviceTokenRequestDTO.class));
        }

        @Test
        @DisplayName("should return 400 when request body is invalid")
        void registerDevice_invalidRequest_returns400() throws Exception {
            // Act & Assert
            mockMvc.perform(post("/api/v1/devices")
                    .header("X-User-Id", "user1")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(invalidRequest)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 400 when request body is empty")
        void registerDevice_emptyBody_returns400() throws Exception {
            // Act & Assert
            mockMvc.perform(post("/api/v1/devices")
                    .header("X-User-Id", "user1")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 500 when X-User-Id header is missing")
        void registerDevice_missingUserIdHeader_returns500() throws Exception {
            // Act & Assert
            mockMvc.perform(post("/api/v1/devices")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(androidRequest)))
                    .andExpect(status().isInternalServerError());
        }
    }

    // ================================================================
    // DELETE /api/v1/devices/{fcmToken} tests
    // ================================================================

    @Nested
    @DisplayName("DELETE /api/v1/devices/{fcmToken}")
    class UnregisterDeviceTests {

        @Test
        @DisplayName("should unregister device token")
        void unregisterDevice_validToken_returns200() throws Exception {
            // Act & Assert
            mockMvc.perform(delete("/api/v1/devices/fcm-token-android-12345")
                    .header("X-User-Id", "user1"))
                    .andExpect(status().isOk());

            verify(pushNotificationService).unregisterDeviceToken(
                    "user1", "fcm-token-android-12345");
        }

        @Test
        @DisplayName("should handle URL-encoded token")
        void unregisterDevice_encodedToken_returns200() throws Exception {
            String encodedToken = "fcm+token%2Fwith%2Fspecial%3Dchars";

            // Act & Assert
            mockMvc.perform(delete("/api/v1/devices/" + encodedToken)
                    .header("X-User-Id", "user1"))
                    .andExpect(status().isOk());

            verify(pushNotificationService).unregisterDeviceToken(
                    eq("user1"), anyString());
        }

        @Test
        @DisplayName("should return 500 when X-User-Id header is missing")
        void unregisterDevice_missingUserIdHeader_returns500() throws Exception {
            // Act & Assert
            mockMvc.perform(delete("/api/v1/devices/fcm-token-android-12345"))
                    .andExpect(status().isInternalServerError());
        }
    }
}
