package com.chatbackend.chat_application_backend.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

import com.chatbackend.chat_application_backend.entity.ChatRoom;
import com.chatbackend.chat_application_backend.entity.Message;
import com.chatbackend.chat_application_backend.entity.User;
import com.chatbackend.chat_application_backend.repository.ChatRepository;
import com.chatbackend.chat_application_backend.repository.MessageRepository;
import com.chatbackend.chat_application_backend.repository.UserRepository;
import com.chatbackend.chat_application_backend.service.serviceImpl.MessageServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

@ExtendWith(MockitoExtension.class)  // Keep only this for service unit tests
public class MessageServiceTest {

    @Mock
    private MessageRepository messageRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ChatRepository chatRepository;

    @InjectMocks
    private MessageServiceImpl messageService;

    @Test
    void shouldSendMessageSuccessfully() {
        User sender = new User();
        sender.setId(1L);

        ChatRoom chatRoom = new ChatRoom();
        chatRoom.setId(10L);

        Message savedMessage = new Message();
        savedMessage.setId(100L);

        when(userRepository.findById(1L)).thenReturn(Optional.of(sender));
        when(chatRepository.findById(10L)).thenReturn(Optional.of(chatRoom));
        when(messageRepository.save(any(Message.class))).thenReturn(savedMessage);

        Message result = messageService.sendMessage(1L, "Hello", 10L);

        assertNotNull(result);
        assertEquals(100L, result.getId());

        verify(messageRepository, times(1)).save(any(Message.class));
    }

    @Test
    void shouldThrowExceptionIfUserNotFound() {
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        Exception exception = assertThrows(RuntimeException.class, () -> {
            messageService.sendMessage(1L, "Hello", 10L);
        });

        assertEquals("Sender not found", exception.getMessage());
    }
}