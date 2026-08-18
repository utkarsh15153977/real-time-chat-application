package com.chatapplication.group_chat.dto.response;

import com.chatapplication.group_chat.entitty.ReactionType;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReactionResponse {
    private Long reactionId;
    private Long messageId;
    private Long userId;
    private ReactionType reactionType;
    private LocalDateTime createdAt;
}
