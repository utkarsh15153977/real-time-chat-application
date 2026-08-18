package com.chatApplication.notification_service.service.impl;

import com.chatApplication.notification_service.dto.MessageEvent;
import com.chatApplication.notification_service.dto.NotificationEvent;
import com.chatApplication.notification_service.dto.NotificationResponse;
import com.chatApplication.notification_service.entity.Notification;
import com.chatApplication.notification_service.entity.NotificationType;
import com.chatApplication.notification_service.exception.NotificationNotFoundException;
import com.chatApplication.notification_service.kafka.NotificationProducer;
import com.chatApplication.notification_service.mapper.NotificationMapper;
import com.chatApplication.notification_service.repository.NotificationRepository;
import com.chatApplication.notification_service.service.NotificationService;
import jakarta.transaction.Transactional;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional
public class NotificationServiceImpl implements NotificationService {
    private final NotificationRepository repository;
    private final NotificationMapper mapper;
    private final SimpMessagingTemplate messagingTemplate;
    private final NotificationProducer notificationProducer;
    public  NotificationServiceImpl(NotificationRepository repository,
                                    NotificationMapper mapper,
                                    SimpMessagingTemplate messagingTemplate,
                                    NotificationProducer notificationProducer) {
        this.repository = repository;
        this.mapper = mapper;
        this.messagingTemplate = messagingTemplate;
        this.notificationProducer = notificationProducer;
    }

//    @Override
//    public NotificationResponse createNotification(MessageEvent event) {
//
//        Notification notification = Notification.builder()
//                .senderId(event.getSenderId())
//                .receiverId(event.getReceiverId())
//                .title("New Message")
//                .message(event.getContent())
//                .notificationType(NotificationType.MESSAGE)
//                .readStatus(false)
//                .createdAt(LocalDateTime.now())
//                .build();
//
//        Notification saved = repository.save(notification);
//
//        NotificationResponse response = mapper.toResponse(saved);
//
//        messagingTemplate.convertAndSendToUser(
//                saved.getReceiverId(),
//                "/queue/notifications",
//                response
//        );
//
//        return response;
//    }

    @Override
    public NotificationResponse createNotification(MessageEvent event) {

        Notification notification = Notification.builder()
                .senderId(event.getSenderId())
                .receiverId(event.getReceiverId())
                .title("New Message")
                .message(event.getContent())
                .notificationType(NotificationType.MESSAGE)
                .readStatus(false)
                .createdAt(LocalDateTime.now())
                .build();

        // Save notification to database
        Notification saved = repository.save(notification);

        // Convert Entity -> Response DTO
        NotificationResponse response = mapper.toResponse(saved);

        // Convert Entity -> Kafka Event
        NotificationEvent notificationEvent = mapper.toEvent(saved);

        // Publish notification event to Kafka
        notificationProducer.publish(notificationEvent);

        // Push notification via WebSocket
        messagingTemplate.convertAndSendToUser(
                saved.getReceiverId(),
                "/queue/notifications",
                response
        );

        return response;
    }
    @Override
    @Transactional
    public List<NotificationResponse> getAllNotifications(String receiverId) {

        return repository.findByReceiverIdOrderByCreatedAtDesc(receiverId)
                .stream()
                .map(mapper::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public List<NotificationResponse> getUnreadNotifications(String receiverId) {

        return repository
                .findByReceiverIdAndReadStatusFalseOrderByCreatedAtDesc(receiverId)
                .stream()
                .map(mapper::toResponse)
                .toList();
    }

    @Override
    public NotificationResponse markAsRead(Long notificationId) {

        Notification notification = repository.findById(notificationId)
                .orElseThrow(() ->
                        new NotificationNotFoundException(
                                "Notification not found with id : " + notificationId));

        notification.setReadStatus(true);

        Notification updated = repository.save(notification);

        return mapper.toResponse(updated);
    }

    @Override
    public void markAllAsRead(String receiverId) {

        List<Notification> notifications =
                repository.findByReceiverIdAndReadStatusFalseOrderByCreatedAtDesc(receiverId);

        notifications.forEach(notification -> notification.setReadStatus(true));

        repository.saveAll(notifications);
    }

    @Override
    public void deleteNotification(Long notificationId) {

        Notification notification = repository.findById(notificationId)
                .orElseThrow(() ->
                        new NotificationNotFoundException(
                                "Notification not found with id : " + notificationId));

        repository.delete(notification);
    }

    @Override
    @Transactional
    public long getUnreadCount(String receiverId) {

        return repository.countByReceiverIdAndReadStatusFalse(receiverId);
    }
}
