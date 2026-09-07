package com.chatApplication.message_service.dto;

import com.chatApplication.message_service.entity.DeviceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class DeviceTokenRequestDTO {

    @NotBlank(message = "FCM token is required")
    private String fcmToken;

    @NotNull(message = "Device type is required")
    private DeviceType deviceType;
}
