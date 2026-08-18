package com.chatapplication.group_chat.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EditMessageRequest {
    /**
     * Message ID to edit
     */
    @NotNull(message = "Message Id is required")
    private Long messageId;

    /**
     * User requesting the edit
     * (Must be the original sender)
     */
    @NotNull(message = "User Id is required")
    private Long userId;

    /**
     * Updated message content
     */
    @NotBlank(message = "Message cannot be empty")
    @Size(max = 5000, message = "Message cannot exceed 5000 characters")
    private String content;

    /**
     * Version for optimistic locking (optional)
     */
    private Long version;
}
