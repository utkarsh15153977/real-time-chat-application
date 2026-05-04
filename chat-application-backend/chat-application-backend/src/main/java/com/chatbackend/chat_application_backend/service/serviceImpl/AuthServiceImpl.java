package com.chatbackend.chat_application_backend.service.serviceImpl;

import com.chatbackend.chat_application_backend.dto.AuthResponse;
import com.chatbackend.chat_application_backend.entity.User;
import com.chatbackend.chat_application_backend.exception.InvalidCredentialsException;
import com.chatbackend.chat_application_backend.exception.UserAlreadyExistsException;
import com.chatbackend.chat_application_backend.exception.UserNotFoundException;
import com.chatbackend.chat_application_backend.repository.UserRepository;
import com.chatbackend.chat_application_backend.security.JwtUtil;
import com.chatbackend.chat_application_backend.service.AuthService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
@Slf4j
@Service
public class AuthServiceImpl implements AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public AuthServiceImpl(UserRepository userRepository, PasswordEncoder passwordEncoder,  JwtUtil jwtUtil) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    @Override
    @CacheEvict(value = "users", allEntries = true)
    public AuthResponse register(String name, String emailOrPhone, String password, String phone){
        if (userRepository.existsByEmail(emailOrPhone)) {
            throw new UserAlreadyExistsException("Email already registered");
        }

        if (userRepository.existsByPhone(phone)) {
            throw new UserAlreadyExistsException("Phone already registered");
        }

        User user = User.builder()
                .name(name)
                .email(emailOrPhone)
                .phone(phone)
                .password(passwordEncoder.encode(password))
                .online(false)
                .build();

        userRepository.save(user);

        String token = jwtUtil.generateToken(user.getEmail());

        AuthResponse response = new AuthResponse();
        response.setId(user.getId());
        response.setToken(token);
        response.setName(user.getName());
        response.setUsername(user.getName());
        response.setEmail(user.getEmail());
        response.setPhone(user.getPhone());

        return response;
    }

    @Override
    public AuthResponse login(String emailOrPhone, String password){
        User user = userRepository.findByEmail(emailOrPhone)
                .or(() -> userRepository.findByPhone(emailOrPhone))
                .orElseThrow(() ->
                        new UserNotFoundException("User not found with email/phone: " + emailOrPhone)
                );

        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new InvalidCredentialsException("Invalid password");
        }

        user.setOnline(true);
        userRepository.save(user);

        String token = jwtUtil.generateToken(user.getEmail());

        AuthResponse response = new AuthResponse();
        response.setId(user.getId());
        response.setToken(token);
        response.setName(user.getName());
        response.setUsername(user.getName());
        response.setEmail(user.getEmail());
        response.setPhone(user.getPhone());

        return response;
    }

    @Override
    @CacheEvict(value = "users", key = "#userId")
    public void logout(Long userId) {

        User user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new UserNotFoundException("User not found with id: " + userId)
                );
        user.setOnline(false);
        userRepository.save(user);
    }
}
