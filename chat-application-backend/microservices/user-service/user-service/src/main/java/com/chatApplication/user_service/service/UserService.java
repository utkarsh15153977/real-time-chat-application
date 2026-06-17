package com.chatApplication.user_service.service;

import com.chatApplication.user_service.dto.UpdateUserRequest;
import com.chatApplication.user_service.dto.UserResponse;

import java.util.List;

public interface UserService {
    UserResponse getUser(Long id);

    UserResponse updateUser(
            Long id,
            UpdateUserRequest request);

    List<UserResponse> searchUser(
            String keyword);

    void updateStatus(
            Long id,
            Boolean online);
}
