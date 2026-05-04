package com.chatbackend.chat_application_backend.dto;

import com.chatbackend.chat_application_backend.entity.User;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UserRequest {
    private String name;
    @NotBlank
    @Email
    private String email;
    @NotBlank
    @Size(min = 6)
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;
    private String phone;
    private String profilePicture;
    private String statusMessage;

    public UserResponse map(User user) {
        UserResponse dto = new UserResponse();
        dto.setId(user.getId());
        dto.setName(user.getName());
        dto.setEmail(user.getEmail());
        dto.setPhone(user.getPhone());
        dto.setProfilePicture(user.getProfilePicture());
        dto.setOnline(user.getOnline());
        dto.setStatusMessage(user.getStatusMessage());
        return dto;
    }
}
