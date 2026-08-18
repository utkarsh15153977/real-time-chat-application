package com.chatApplication.notification_service.mapper;

import com.chatApplication.notification_service.dto.NotificationEvent;
import com.chatApplication.notification_service.dto.NotificationResponse;
import com.chatApplication.notification_service.entity.Notification;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface NotificationMapper {
    NotificationResponse toResponse(Notification notification);

    Notification toEntity(NotificationResponse response);

    @Mapping(target = "notificationType", source = "notificationType")
    NotificationEvent toEvent(Notification notification);
}
