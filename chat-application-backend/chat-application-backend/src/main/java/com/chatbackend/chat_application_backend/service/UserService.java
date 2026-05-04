package com.chatbackend.chat_application_backend.service;

import com.chatbackend.chat_application_backend.entity.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.List;
import java.util.Optional;

public interface UserService {
    //Basic Crud Operation
    public List<User> getAllUsers();
    public User getUser(Long id);
    public User createUser(User user);
    public User updateUser(User user);
    public void deleteUser(Long id);

    // Authentication support
    Optional<User> findByEmail(String email);
    Optional<User> findByPhone(String phone);

    // Profile management
    User updateProfilePicture(Long userId, String pictureUrl);
    User updateUserStatus(Long userId, String statusMessage);

    // Online / Offline tracking
    void setUserOnline(Long userId);
    void setUserOffline(Long userId);
    boolean isUserOnline(Long userId);

    // Search users
    List<User> searchUsersByName(String name);

    // Contact related
    List<User> getUserContacts(Long userId);

    //It is used in CustomUserDetailsService to load user details for authentication.
    //public UserDetails loadUserByUsername(String username)
            //throws UsernameNotFoundException;

    Optional<User> findByEmailOrPhone(String value);
}
