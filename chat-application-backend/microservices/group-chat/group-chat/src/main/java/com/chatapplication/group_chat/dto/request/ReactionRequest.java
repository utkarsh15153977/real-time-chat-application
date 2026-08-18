package com.chatapplication.group_chat.dto.request;

import com.chatapplication.group_chat.entitty.MessageStatus;
import com.chatapplication.group_chat.entitty.ReactionType;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReactionRequest {
    @NotNull(message = "Message Id is required")
    private Long messageId;

    @NotNull(message = "User Id is required")
    private Long userId;

    @NotNull(message = "Reaction type is required")
    private ReactionType reactionType;
}
