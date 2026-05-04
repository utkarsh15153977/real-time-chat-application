package com.chatbackend.chat_application_backend.mapper;

import com.chatbackend.chat_application_backend.dto.UserResponse;
import com.chatbackend.chat_application_backend.entity.User;

import java.util.List;

public class UserMapper {
    public static UserResponse toUserResponseDTO(User user) {
        if(user == null) return null;
        UserResponse userResponseDTO = new UserResponse();

        userResponseDTO.setId(user.getId());
        userResponseDTO.setEmail(user.getEmail());
        userResponseDTO.setPhone(user.getPhone());
        userResponseDTO.setName(user.getName());
        userResponseDTO.setProfilePicture(user.getProfilePicture());
        userResponseDTO.setOnline(user.getOnline());
        userResponseDTO.setStatusMessage(user.getStatusMessage());

        return userResponseDTO;
    }

    public static List<UserResponse> toUserResponseDTO(List<User> users) {
        return users.stream()
                .map(UserMapper::toUserResponseDTO)
                .toList();
    }
}