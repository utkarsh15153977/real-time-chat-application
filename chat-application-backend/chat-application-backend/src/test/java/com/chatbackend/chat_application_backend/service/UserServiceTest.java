package com.chatbackend.chat_application_backend.service;

import com.chatbackend.chat_application_backend.entity.User;
import com.chatbackend.chat_application_backend.repository.UserRepository;
import com.chatbackend.chat_application_backend.service.serviceImpl.UserServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class UserServiceTest {
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserServiceImpl userService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Test
    void shouldCreateUser() {

        User input = new User();
        input.setName("test"); // OR setEmail based on your entity

        User savedUser = new User();
        savedUser.setId(1L);
        savedUser.setName("test");

        when(userRepository.save(any(User.class))).thenReturn(savedUser);
        when(passwordEncoder.encode(any())).thenReturn("encoded");

        User result = userService.createUser(input);

        assertNotNull(result);
        assertEquals("test", result.getName());
    }
}
