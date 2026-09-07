package com.chatbackend.chat_application_backend.service.serviceImpl;

import com.chatbackend.chat_application_backend.dto.StatusAckDTO;
import com.chatbackend.chat_application_backend.entity.ChatRoom;
import com.chatbackend.chat_application_backend.entity.Message;
import com.chatbackend.chat_application_backend.entity.MessageStatus;
import com.chatbackend.chat_application_backend.entity.User;
import com.chatbackend.chat_application_backend.exception.ChatNotFoundException;
import com.chatbackend.chat_application_backend.exception.MessageNotFoundException;
import com.chatbackend.chat_application_backend.exception.UserNotFoundException;
import com.chatbackend.chat_application_backend.repository.ChatRepository;
import com.chatbackend.chat_application_backend.repository.MessageRepository;
import com.chatbackend.chat_application_backend.repository.UserRepository;
import com.chatbackend.chat_application_backend.service.MessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MessageServiceImpl implements MessageService {

    private final MessageRepository messageRepository;
    private final ChatRepository chatRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;

    private User getUserOrThrow(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
    }

    private ChatRoom getChatRoomOrThrow(Long chatRoomId) {
        return chatRepository.findById(chatRoomId)
                .orElseThrow(() -> new ChatNotFoundException("Chat room not found"));
    }

    private void sendAckEvent(Long chatRoomId, Long messageId, Long userId, MessageStatus status) {
        StatusAckDTO ack = new StatusAckDTO(
                messageId, chatRoomId, userId, status, LocalDateTime.now());
        messagingTemplate.convertAndSend("/topic/chat/" + chatRoomId + "/ack", ack);
    }

    @Override
    @CacheEvict(value = "chatMessages", key = "#chatRoomId")
    public Message sendMessage(Long senderId, String content, Long chatRoomId) {
        log.info("Sending message from Id: {}", senderId);
        if (content == null || content.trim().isEmpty()) {
            throw new IllegalArgumentException("Message content cannot be empty");
        }

        User sender = getUserOrThrow(senderId);
        ChatRoom chatRoom = getChatRoomOrThrow(chatRoomId);

        Message message = Message.builder()
                .sender(sender)
                .content(content)
                .chatRoom(chatRoom)
                .timestamp(LocalDateTime.now())
                .status(MessageStatus.SENT)
                .build();

        return messageRepository.save(message);
    }

    @Override
    @Cacheable(value = "chatMessages", key = "#chatRoomId")
    public List<Message> getMessagesByChatRoom(Long chatRoomId) {
        log.info("Fetching messages for chatRoom {}", chatRoomId);
        getChatRoomOrThrow(chatRoomId);
        return messageRepository.findByChatRoom_IdOrderByTimestampAsc(chatRoomId);
    }

    @Override
    @Cacheable(value = "messages", key = "#messageId")
    public Message getMessage(Long messageId) {
        log.info("Fetching message {}", messageId);
        return messageRepository.findById(messageId)
                .orElseThrow(() -> new MessageNotFoundException("Message not found with id: " + messageId));
    }

    @Override
    @Caching(evict = {
            @CacheEvict(value = "chatMessages", key = "#message.chatRoom.id"),
            @CacheEvict(value = "messages", key = "#message.id")
    })
    public void deleteMessage(Long messageId) {
        log.warn("Deleting message {}", messageId);
        Message message = getMessage(messageId);
        messageRepository.delete(message);
    }

    @Override
    public void markMessageDelivered(Long messageId) {
        log.info("Marking message {} as DELIVERED", messageId);
        Message message = getMessage(messageId);
        message.setStatus(MessageStatus.DELIVERED);
        messageRepository.save(message);
    }

    @Override
    public void markMessageRead(Long messageId) {
        log.info("Marking message {} as READ", messageId);
        Message message = getMessage(messageId);
        message.setStatus(MessageStatus.READ);
        messageRepository.save(message);
    }

    @Override
    @Transactional
    public void markAsDelivered(Long messageId, Long userId) {
        log.info("Marking message {} as DELIVERED by user {}", messageId, userId);
        Message message = getMessage(messageId);
        message.setStatus(MessageStatus.DELIVERED);
        messageRepository.save(message);
        sendAckEvent(message.getChatRoom().getId(), messageId, userId, MessageStatus.DELIVERED);
    }

    @Override
    @Transactional
    public void markAsRead(Long chatRoomId, Long userId) {
        log.info("Marking all messages in chat {} as READ by user {}", chatRoomId, userId);
        ChatRoom chatRoom = getChatRoomOrThrow(chatRoomId);
        List<Message> messages = messageRepository.findByChatRoom_IdOrderByTimestampAsc(chatRoomId);

        int updatedCount = 0;
        for (Message message : messages) {
            if (!message.getSender().getId().equals(userId)
                    && (message.getStatus() == MessageStatus.SENT || message.getStatus() == MessageStatus.DELIVERED)) {
                message.setStatus(MessageStatus.READ);
                updatedCount++;
            }
        }

        if (updatedCount > 0) {
            messageRepository.saveAll(messages);
            sendAckEvent(chatRoomId, null, userId, MessageStatus.READ);
        }
    }

    @Override
    @Caching(evict = {
            @CacheEvict(value = "messages", key = "#messageId"),
            @CacheEvict(value = "chatMessages", key = "#message.chatRoom.id")
    })
    public Message editMessage(Long messageId, String newContent) {
        log.info("Editing message {}", messageId);
        Message message = getMessage(messageId);
        message.setContent(newContent);
        return messageRepository.save(message);
    }

    @Override
    public void markAllMessagesDelivered(Long chatRoomId, Long userId) {
        log.info("Marking messages delivered for chat {}", chatRoomId);
        getChatRoomOrThrow(chatRoomId);
        List<Message> messages = messageRepository.findByChatRoom_IdOrderByTimestampAsc(chatRoomId);
        for (Message message : messages) {
            if (!message.getSender().getId().equals(userId)
                    && message.getStatus() == MessageStatus.SENT) {
                message.setStatus(MessageStatus.DELIVERED);
            }
        }
        messageRepository.saveAll(messages);
    }

    @Override
    public void markAllMessagesRead(Long chatRoomId, Long userId) {
        log.info("Marking messages read for chat {}", chatRoomId);
        getChatRoomOrThrow(chatRoomId);
        List<Message> messages = messageRepository.findByChatRoom_IdOrderByTimestampAsc(chatRoomId);
        for (Message message : messages) {
            if (!message.getSender().getId().equals(userId)
                    && message.getStatus() == MessageStatus.DELIVERED) {
                message.setStatus(MessageStatus.READ);
            }
        }
        messageRepository.saveAll(messages);
    }

    @Override
    public long getUnreadMessageCount(Long userId, Long chatRoomId) {
        log.info("Fetching unread message count for user {} in chat {}", userId, chatRoomId);
        return messageRepository.countByChatRoom_IdAndSender_IdNotAndStatus(
                chatRoomId, userId, MessageStatus.READ);
    }

    @Override
    public List<Message> searchMessages(Long chatRoomId, String keyword) {
        log.info("Searching messages for chat {} with keyword {}", chatRoomId, keyword);
        return messageRepository.findByChatRoom_IdAndContentContainingIgnoreCase(chatRoomId, keyword);
    }
}
