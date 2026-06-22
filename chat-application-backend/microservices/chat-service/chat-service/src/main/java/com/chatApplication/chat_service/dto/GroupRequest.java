package com.chatApplication.chat_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class GroupRequest {

    @NotBlank(message = "Group name is required")
    private String name;

    private String groupIcon;

    @NotNull(message = "Admin id is required")
    private Long adminId;

    @NotEmpty(message = "At least one member is required")
    private List<Long> members;
}