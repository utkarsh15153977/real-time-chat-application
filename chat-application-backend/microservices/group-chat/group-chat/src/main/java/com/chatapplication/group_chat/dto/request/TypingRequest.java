package com.chatapplication.group_chat.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TypingRequest {
    /**
     * User who is typing
     */
    @NotNull(message = "User Id is required")
    private Long userId;

    /**
     * Receiver User ID
     * (Required for one-to-one chat)
     */
    private Long receiverId;

    /**
     * Group ID
     * (Required for group chat)
     */
    private Long groupId;

    /**
     * Indicates whether the user is currently typing
     */
    @NotNull(message = "Typing status is required")
    private Boolean typing;
}
