package com.chatApplication.chat_service.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RenameGroupChat {
    @NotBlank(message = "Group name is required")
    private String name;
}
