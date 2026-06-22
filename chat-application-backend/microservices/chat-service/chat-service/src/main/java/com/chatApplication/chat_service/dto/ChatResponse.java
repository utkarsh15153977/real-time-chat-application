package com.chatApplication.chat_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatResponse {
    private Long chatId;
    @NotBlank
    private String name;
    @NotNull
    private Boolean group;
    @NotEmpty
    private String groupIcon;
}
