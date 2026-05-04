package com.chatbackend.chat_application_backend.service.serviceImpl;

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
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
@Service
public class MessageServiceImpl implements MessageService {
    private final MessageRepository messageRepository;
    private final ChatRepository chatRepository;
    private final UserRepository userRepository;

    public  MessageServiceImpl(MessageRepository messageRepository, ChatRepository chatRepository, UserRepository userRepository) {
        this.messageRepository = messageRepository;
        this.chatRepository = chatRepository;
        this.userRepository = userRepository;
    }

    private static final Logger log = LoggerFactory.getLogger(MessageServiceImpl.class);

    @Override
    @CacheEvict(value = "chatMessages", key = "#chatRoomId")
    public Message sendMessage(Long senderId, String content, Long chatRoomId){
        log.info("Sending message from Id : {}", senderId);
        if(content == null || content.trim().isEmpty()){
            throw new IllegalArgumentException("Message content cannot be empty");
        }

        User sender = userRepository.findById(senderId)
                .orElseThrow(() -> new UserNotFoundException("Sender not found"));

        ChatRoom chatRoom = chatRepository.findById(chatRoomId)
                .orElseThrow(() -> new ChatNotFoundException("Chat room not found"));

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
    public List<Message> getMessagesByChatRoom(Long chatRoomId){
        log.info("Fetching messages for chatRoom {}", chatRoomId);
        chatRepository.findById(chatRoomId)
                .orElseThrow(() ->
                        new ChatNotFoundException("Chat room not found"));
        return messageRepository.findByChatRoom_IdOrderByTimestampAsc(chatRoomId);
    }

    @Override
    @Cacheable(value = "messages", key = "#messageId")
    public Message getMessage(Long messageId){
        log.info("Sending message from Id : {}", messageId);
        return messageRepository.findById(messageId)
                .orElseThrow(() ->
                        new MessageNotFoundException("Message not found with id: "
                                + messageId));
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

        chatRepository.findById(chatRoomId)
                .orElseThrow(() ->
                        new ChatNotFoundException("Chat room not found"));
        List<Message> messages = messageRepository
                .findByChatRoom_IdOrderByTimestampAsc(chatRoomId);
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

        chatRepository.findById(chatRoomId)
                .orElseThrow(() ->
                        new ChatNotFoundException("Chat room not found"));
        List<Message> messages = messageRepository
                .findByChatRoom_IdOrderByTimestampAsc(chatRoomId);
        for (Message message : messages) {
            if (!message.getSender().getId().equals(userId)
                    && message.getStatus() == MessageStatus.DELIVERED) {
                message.setStatus(MessageStatus.READ);
            }
        }
        messageRepository.saveAll(messages);
    }

    @Override
    public long getUnreadMessageCount(Long userId, Long chatRoomId){
    //log.info("Marking messages read for chat {}", chatRoomId);

        log.info("Fetching unread message count for user {}", userId);
        return messageRepository.countByChatRoom_IdAndSender_IdNotAndStatus(
                chatRoomId,
                userId,
                MessageStatus.READ);
    }

    @Override
    public List<Message> searchMessages(Long chatRoomId, String keyword){
        log.info("Searching messages for chat {}", chatRoomId);
        return messageRepository.findByChatRoom_IdAndContentContainingIgnoreCase(chatRoomId, keyword);
    }
}
