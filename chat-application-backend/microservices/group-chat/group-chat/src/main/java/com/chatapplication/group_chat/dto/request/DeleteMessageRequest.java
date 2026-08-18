package com.chatapplication.group_chat.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeleteMessageRequest {
    /**
     * Message ID to delete
     */
    @NotNull(message = "Message Id is required")
    private Long messageId;

    /**
     * User requesting the deletion
     */
    @NotNull(message = "User Id is required")
    private Long userId;

    /**
     * Delete for everyone
     * false = Delete only for requester
     * true = Delete for everyone
     */
    @Builder.Default
    private Boolean deleteForEveryone = false;
}
