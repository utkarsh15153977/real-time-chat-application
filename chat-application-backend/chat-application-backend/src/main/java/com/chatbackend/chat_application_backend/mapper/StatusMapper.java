package com.chatbackend.chat_application_backend.mapper;

import com.chatbackend.chat_application_backend.dto.StatusMessageResponse;
import com.chatbackend.chat_application_backend.entity.Status;

import java.util.List;

public class StatusMapper {
    public static StatusMessageResponse toDTO(Status status) {

        if (status == null) return null;

        StatusMessageResponse dto = new StatusMessageResponse();

        dto.setSenderId(status.getUser().getId());
        dto.setImageUrl(status.getImageUrl());
        dto.setCreatedAt(status.getCreatedAt());
        dto.setExpiresAt(status.getExpiresAt());

        return dto;
    }

    public static List<StatusMessageResponse> toDTOList(List<Status> statuses) {
        return statuses.stream()
                .map(StatusMapper::toDTO)
                .toList();
    }
}
