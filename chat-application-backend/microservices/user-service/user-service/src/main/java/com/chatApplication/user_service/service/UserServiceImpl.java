package com.chatApplication.user_service.service;

import com.chatApplication.user_service.dto.UpdateUserRequest;
import com.chatApplication.user_service.dto.UserResponse;
import com.chatApplication.user_service.entity.UserProfile;
import com.chatApplication.user_service.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class UserServiceImpl implements UserService {

    private final UserRepository repository;

    public UserServiceImpl(
            UserRepository repository){
        this.repository=repository;
    }

    @Override
    public UserResponse getUser(Long id) {
        return repository.findById(id)
                .map(user -> new UserResponse(
                        user.getUserId(),
                        user.getName(),
                        user.getEmail(),
                        user.getPhone(),
                        user.getProfilePicture(),
                        user.getStatusMessage(),
                        user.getOnline(),
                        user.getLastSeen()
                ))
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    @Override
    public UserResponse updateUser(Long id, UpdateUserRequest request) {
        UserProfile user=
                repository.findById(id)
                        .orElseThrow(
                                ()->new RuntimeException(
                                        "User not found"));

        user.setName(request.getName());
        user.setPhone(request.getPhone());
        user.setProfilePicture(
                request.getProfilePicture());

        user.setStatusMessage(
                request.getStatusMessage());

        repository.save(user);

        return map(user);
    }

    @Override
    public List<UserResponse> searchUser(String keyword) {
        return repository
                .findByNameContainingIgnoreCase(
                        keyword)
                .stream()
                .map(this::map)
                .toList();
    }

    @Override
    public void updateStatus(Long id, Boolean online) {
        UserProfile user=
                repository.findById(id)
                        .orElseThrow(
                                ()->new RuntimeException(
                                        "User not found"));

        user.setOnline(online);
        user.setLastSeen(
                LocalDateTime.now());

        repository.save(user);
    }

    private UserResponse map(
            UserProfile user){

        return new UserResponse(
                user.getUserId(),
                user.getName(),
                user.getEmail(),
                user.getPhone(),
                user.getProfilePicture(),
                user.getStatusMessage(),
                user.getOnline(),
                user.getLastSeen()
        );
    }
}
