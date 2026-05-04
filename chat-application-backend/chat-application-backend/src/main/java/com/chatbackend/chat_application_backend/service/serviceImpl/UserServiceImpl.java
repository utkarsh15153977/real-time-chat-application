package com.chatbackend.chat_application_backend.service.serviceImpl;

import com.chatbackend.chat_application_backend.entity.User;
import com.chatbackend.chat_application_backend.exception.UserAlreadyExistsException;
import com.chatbackend.chat_application_backend.exception.UserNotFoundException;
import com.chatbackend.chat_application_backend.repository.UserRepository;
import com.chatbackend.chat_application_backend.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserServiceImpl(UserRepository userRepository,
                           PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    // CRUD
    @Override
    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    @Override
    @Cacheable(value = "users", key = "#id")
    public User getUser(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() ->
                        new UserNotFoundException("User not found"));
    }

    @Override
    @CacheEvict(value = "users", allEntries = true)
    public User createUser(User user) {

        if (userRepository.existsByEmail(user.getEmail())) {
            throw new UserAlreadyExistsException("Email already exists");
        }

        if (userRepository.existsByPhone(user.getPhone())) {
            throw new UserAlreadyExistsException("Phone already exists");
        }

        user.setPassword(passwordEncoder.encode(user.getPassword()));
        user.setOnline(false);

        return userRepository.save(user);
    }

    @Override
    @Caching(evict = {
            @CacheEvict(value = "users", key = "#user.id"),
            @CacheEvict(value = "users", key = "#user.email"),
            @CacheEvict(value = "users", key = "#user.phone")
    })
    public User updateUser(User user) {

        User existingUser = userRepository.findById(user.getId())
                .orElseThrow(() ->
                        new UserNotFoundException("User not found"));

        if (!existingUser.getPhone().equals(user.getPhone()) &&
                userRepository.existsByPhone(user.getPhone())) {
            throw new UserAlreadyExistsException("Phone already exists");
        }

        existingUser.setName(user.getName());
        existingUser.setEmail(user.getEmail());
        existingUser.setPhone(user.getPhone());

        if (user.getPassword() != null && !user.getPassword().isEmpty()) {
            existingUser.setPassword(
                    passwordEncoder.encode(user.getPassword())
            );
        }
        return userRepository.save(existingUser);
    }

    @Override
    @CacheEvict(value = "users", key = "#id")
    public void deleteUser(Long id) {

        User user = userRepository.findById(id)
                .orElseThrow(() ->
                        new UserNotFoundException("User not found"));
        userRepository.delete(user);
    }

    // Authentication support
    @Override
    @Cacheable(value = "users", key = "#email")
    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    @Override
    @Cacheable(value = "users", key = "#phone")
    public Optional<User> findByPhone(String phone) {
        return userRepository.findByPhone(phone);
    }

    // Profile
    @Override
    @CacheEvict(value = "users", key = "#userId")
    public User updateProfilePicture(Long userId, String pictureUrl) {
        User user = getUser(userId);
        user.setProfilePicture(pictureUrl);
        return userRepository.save(user);
    }

    @Override
    @CacheEvict(value = "users", key = "#userId")
    public User updateUserStatus(Long userId, String statusMessage) {
        User user = getUser(userId);
        user.setStatusMessage(statusMessage);
        return userRepository.save(user);
    }

    // Online status
    @Override
    @CacheEvict(value = "users", key = "#userId")
    public void setUserOnline(Long userId) {
        User user = getUser(userId);
        user.setOnline(true);
        userRepository.save(user);
    }

    @Override
    @CacheEvict(value = "users", key = "#userId")
    public void setUserOffline(Long userId) {
        User user = getUser(userId);
        user.setOnline(false);
        userRepository.save(user);
    }

    @Override
    @Cacheable(value = "userOnlineStatus", key = "#userId")
    public boolean isUserOnline(Long userId) {
        User user = getUser(userId);
        return user.getOnline();
    }

    // Search
    @Override
    @Cacheable(value = "userSearches", key = "#name")
    public List<User> searchUsersByName(String name) {
        return userRepository.findByNameContainingIgnoreCase(name);
    }

    // Contacts
    @Override
    @Cacheable(value = "userContacts", key = "#userId")
    public List<User> getUserContacts(Long userId) {
        List<User> users = userRepository.findAll();
        users.removeIf(user -> user.getId().equals(userId));
        return users;
    }

    @Override
    public Optional<User> findByEmailOrPhone(String value) {
        return userRepository.findByEmail(value)
                .or(() -> userRepository.findByPhone(value));
    }
}