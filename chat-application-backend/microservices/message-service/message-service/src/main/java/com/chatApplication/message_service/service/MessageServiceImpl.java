//package com.chatApplication.message_service.service;
//
//import com.chatApplication.message_service.dto.AttachmentRequest;
//import com.chatApplication.message_service.dto.MessageRequest;
//import com.chatApplication.message_service.dto.MessageResponse;
//import com.chatApplication.message_service.entity.MessageStatus;
//import com.chatApplication.message_service.repository.MessageRepository;
//import jakarta.transaction.Transactional;
//import lombok.RequiredArgsConstructor;
//import com.chatApplication.message_service.entity.Message;
//import org.springframework.stereotype.Service;
//
//import java.time.LocalDateTime;
//import java.util.List;
//
//@Service
//@RequiredArgsConstructor
//@Transactional
//public class MessageServiceImpl<Message> implements MessageService {
//
//    private final MessageRepository messageRepository;
//
//    @Override
//    public MessageResponse sendMessage(MessageRequest request) {
//
//        Message message = Message.builder()
//                .senderId(request.getSenderId())
//                .receiverId(request.getReceiverId())
//                .content(request.getContent())
//                .status(MessageStatus.SENT)
//                .createdAt(LocalDateTime.now())
//                .build();
//
//        return mapToResponse(
//                messageRepository.save(message));
//    }
//
//    @Override
//    public List<MessageResponse> getConversation(
//            String user1,
//            String user2) {
//
//        return messageRepository
//                .findConversation(user1, user2)
//                .stream()
//                .map(this::mapToResponse)
//                .toList();
//    }
//
//    @Override
//    public MessageResponse getMessage(Long messageId) {
//
//        Message message = messageRepository.findById(messageId)
//                .orElseThrow(() ->
//                        new RuntimeException("Message not found"));
//
//        return mapToResponse(message);
//    }
//
//    @Override
//    public MessageResponse markAsDelivered(Long messageId) {
//
//        Message message = messageRepository.findById(messageId)
//                .orElseThrow(() ->
//                        new RuntimeException("Message not found"));
//
//        message.setStatus(MessageStatus.DELIVERED);
//
//        return mapToResponse(
//                messageRepository.save(message));
//    }
//
//    @Override
//    public MessageResponse markAsSeen(Long messageId) {
//
//        Message message = messageRepository.findById(messageId)
//                .orElseThrow(() ->
//                        new RuntimeException("Message not found"));
//
//        message.setStatus(MessageStatus.SEEN);
//
//        return mapToResponse(
//                messageRepository.save(message));
//    }
//
//    @Override
//    public void markAllAsSeen(
//            String senderId,
//            String receiverId) {
//
//        List<Message> messages =
//                messageRepository.findUnreadMessages(
//                        senderId,
//                        receiverId);
//
//        messages.forEach(
//                msg -> msg.setStatus(MessageStatus.SEEN));
//
//        messageRepository.saveAll(messages);
//    }
//
//    @Override
//    public void deleteMessage(Long messageId) {
//
//        messageRepository.deleteById(messageId);
//    }
//
//    @Override
//    public MessageResponse editMessage(
//            Long messageId,
//            String content) {
//
//        Message message = messageRepository.findById(messageId)
//                .orElseThrow(() ->
//                        new RuntimeException("Message not found"));
//
//        message.setContent(content);
//        message.setEdited(true);
//
//        return mapToResponse(
//                messageRepository.save(message));
//    }
//
//    @Override
//    public Long getUnreadCount(
//            String senderId,
//            String receiverId) {
//
//        return messageRepository.countUnreadMessages(
//                senderId,
//                receiverId);
//    }
//
//    @Override
//    public List<MessageResponse> getRecentMessages(
//            String userId) {
//
//        return messageRepository
//                .findRecentMessages(userId)
//                .stream()
//                .map(this::mapToResponse)
//                .toList();
//    }
//
//    @Override
//    public boolean exists(Long messageId) {
//
//        return messageRepository.existsById(messageId);
//    }
//
//    @Override
//    public MessageResponse sendAttachment(
//            AttachmentRequest request) {
//
//        Message message = Message.builder()
//                .senderId(request.getSenderId())
//                .receiverId(request.getReceiverId())
//                .attachmentUrl(request.getAttachmentUrl())
//                .attachmentType(request.getAttachmentType())
//                .status(MessageStatus.SENT)
//                .createdAt(LocalDateTime.now())
//                .build();
//
//        return mapToResponse(
//                messageRepository.save(message));
//    }
//
//    private MessageResponse mapToResponse(
//            Message message) {
//
//        return MessageResponse.builder()
//                .id(message.getId())
//                .senderId(message.getSenderId())
//                .receiverId(message.getReceiverId())
//                .content(message.getContent())
//                .attachmentUrl(message.getAttachmentUrl())
//                .status(message.getStatus())
//                .createdAt(message.getCreatedAt())
//                .build();
//    }
//}

package com.chatApplication.message_service.service;

import com.chatApplication.message_service.dto.*;
import com.chatApplication.message_service.entity.Message;
import com.chatApplication.message_service.entity.MessageStatus;
import com.chatApplication.message_service.kafka.MessageEvent;
import com.chatApplication.message_service.kafka.MessageProducer;
import com.chatApplication.message_service.repository.MessageRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class MessageServiceImpl implements MessageService {

    private final MessageRepository messageRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final MessageProducer messageProducer;

//    @Override
//    public MessageResponse sendMessage(
//            MessageRequest request) {
//
//        Message message = Message.builder()
//                .senderId(request.getSenderId())
//                .receiverId(request.getReceiverId())
//                .content(request.getMessage())
//                .status(MessageStatus.SENT)
//                .isAttachment(false)
//                .build();
//
//        Message savedMessage =
//                messageRepository.save(message);
//
//        ChatMessage chatMessage =
//                ChatMessage.builder()
//                        .msgId(savedMessage.getMsgId())
//                        .senderId(savedMessage.getSenderId())
//                        .receiverId(savedMessage.getReceiverId())
//                        .content(savedMessage.getContent())
//                        .status(savedMessage.getStatus().name())
//                        .build();
//
//
//
//        messagingTemplate.convertAndSendToUser(
//                savedMessage.getReceiverId(),
//                "/queue/messages",
//                chatMessage);
//
//        return mapToResponse(savedMessage);
//    }

    @Override
    public MessageResponse sendMessage(
            MessageRequest request) {

        Message message = Message.builder()
                .senderId(request.getSenderId())
                .receiverId(request.getReceiverId())
                .content(request.getMessage())
                .status(MessageStatus.SENT)
                .isAttachment(false)
                .build();

        Message savedMessage =
                messageRepository.save(message);

        // WebSocket Real-Time Delivery
        ChatMessage chatMessage =
                ChatMessage.builder()
                        .msgId(savedMessage.getMsgId())
                        .senderId(savedMessage.getSenderId())
                        .receiverId(savedMessage.getReceiverId())
                        .content(savedMessage.getContent())
                        .status(savedMessage.getStatus().name())
                        .build();

        messagingTemplate.convertAndSendToUser(
                savedMessage.getReceiverId(),
                "/queue/messages",
                chatMessage);

        // Kafka Event Publish
        messageProducer.publish(
                MessageEvent.builder()
                        .messageId(savedMessage.getMsgId())
                        .senderId(savedMessage.getSenderId())
                        .receiverId(savedMessage.getReceiverId())
                        .content(savedMessage.getContent())
                        .status(savedMessage.getStatus().name())
                        .build()
        );

        return mapToResponse(savedMessage);
    }

    @Override
    public List<MessageResponse> getConversation(
            String user1,
            String user2) {

        List<MessageResponse> responses = new ArrayList<>();

        List<Message> messages =
                messageRepository.findConversation(
                        user1,
                        user2);

        for (Message message : messages) {
            responses.add(
                    mapToResponse(message));
        }

        return responses;
    }

    @Override
    public MessageResponse getMessage(Long messageId) {

        Message message =
                messageRepository.findById(messageId)
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Message not found"));

        return mapToResponse(message);
    }

//    @Override
//    public MessageResponse markAsDelivered(Long messageId) {
//
//        Message message =
//                messageRepository.findById(messageId)
//                        .orElseThrow(() ->
//                                new RuntimeException(
//                                        "Message not found"));
//
//        message.setStatus(
//                MessageStatus.DELIVERED);
//
//        return mapToResponse(
//                messageRepository.save(message));
//    }

    @Override
    public MessageResponse markAsDelivered(
            Long messageId) {

        Message message =
                messageRepository.findById(messageId)
                        .orElseThrow();

        if(message.getStatus() ==
                MessageStatus.SENT){

            message.setStatus(
                    MessageStatus.DELIVERED);
        }

        Message updated =
                messageRepository.save(message);

        sendReceipt(updated);

        return mapToResponse(updated);
    }

//    @Override
//    public MessageResponse markAsSeen(Long messageId) {
//
//        Message message =
//                messageRepository.findById(messageId)
//                        .orElseThrow(() ->
//                                new RuntimeException(
//                                        "Message not found"));
//
//        message.setStatus(
//                MessageStatus.SEEN);
//
//        return mapToResponse(
//                messageRepository.save(message));
//    }

    @Override
    public MessageResponse markAsSeen(
            Long messageId) {

        Message message =
                messageRepository.findById(messageId)
                        .orElseThrow();

        message.setStatus(
                MessageStatus.SEEN);

        Message updated =
                messageRepository.save(message);

        sendReceipt(updated);

        return mapToResponse(updated);
    }

    @Override
    public void markAllAsSeen(
            String senderId,
            String receiverId) {

        List<Message> messages =
                messageRepository.findUnreadMessages(
                        senderId,
                        receiverId);

        for (Message message : messages) {

            message.setStatus(
                    MessageStatus.SEEN);
        }

        messageRepository.saveAll(messages);
    }

    @Override
    public void deleteMessage(Long messageId) {

        if (!messageRepository.existsById(messageId)) {

            throw new RuntimeException(
                    "Message not found");
        }

        messageRepository.deleteById(messageId);
    }

    @Override
    public MessageResponse editMessage(
            Long messageId,
            String content) {

        Message message =
                messageRepository.findById(messageId)
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Message not found"));

        message.setContent(content);

        return mapToResponse(
                messageRepository.save(message));
    }

    @Override
    public Long getUnreadCount(
            String senderId,
            String receiverId) {

        return messageRepository.countUnreadMessages(
                senderId,
                receiverId);
    }

    @Override
    public List<MessageResponse> getRecentMessages(
            String userId) {

        List<MessageResponse> responses =
                new ArrayList<>();

        List<Message> messages =
                messageRepository.findRecentMessages(
                        userId);

        for (Message message : messages) {

            responses.add(
                    mapToResponse(message));
        }

        return responses;
    }

    @Override
    public boolean exists(Long messageId) {

        return messageRepository.existsById(
                messageId);
    }

    @Override
    public MessageResponse sendAttachment(
            AttachmentRequest request) {

        Message message = Message.builder()
                .senderId(request.getSenderId())
                .receiverId(request.getReceiverId())
                .status(MessageStatus.SENT)
                .isAttachment(true)
                .attachmentName(
                        request.getFile()
                                .getOriginalFilename())
                .attachmentSize(
                        request.getFile()
                                .getSize())
                .attachmentType(
                        request.getFile()
                                .getContentType())
                .attachmentUrl(
                        "/uploads/"
                                + request.getFile()
                                .getOriginalFilename())
                .build();

        return mapToResponse(
                messageRepository.save(message));
    }

//    private MessageResponse mapToResponse(
//            Message message) {
//
//        return MessageResponse.builder()
//                .msgId(message.getMsgId())
//                .senderId(message.getSenderId())
//                .receiverId(message.getReceiverId())
//                .content(message.getContent())
//                .sendTime(message.getTimestamp())
//                .delivered(
//                        message.getStatus()
//                                == MessageStatus.DELIVERED
//                                || message.getStatus()
//                                == MessageStatus.SEEN)
//                .seen(
//                        message.getStatus()
//                                == MessageStatus.SEEN)
//                .build();
//    }

    private MessageResponse mapToResponse(
            Message message) {

        return MessageResponse.builder()
                .msgId(message.getMsgId())
                .senderId(message.getSenderId())
                .receiverId(message.getReceiverId())
                .content(message.getContent())
                .sendTime(message.getTimestamp())
                .status(message.getStatus())
                .build();
    }

    private void sendReceipt(
            Message message) {

        ReadReceiptEvent receipt =
                ReadReceiptEvent.builder()
                        .messageId(message.getMsgId())
                        .senderId(message.getSenderId())
                        .receiverId(message.getReceiverId())
                        .status(message.getStatus().name())
                        .build();

        messagingTemplate.convertAndSendToUser(
                message.getSenderId(),
                "/queue/receipts",
                receipt);
    }
}