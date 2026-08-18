package com.chatapplication.group_chat.dto.request;

import com.chatapplication.group_chat.entitty.MessageStatus;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageStatusRequest {
    /**
     * Message ID
     */
    @NotNull(message = "Message Id is required")
    private Long messageId;

    /**
     * User updating the status
     */
    @NotNull(message = "User Id is required")
    private Long userId;

    /**
     * SENT / DELIVERED / SEEN
     */
    @NotNull(message = "Message status is required")
    private MessageStatus status;
}
