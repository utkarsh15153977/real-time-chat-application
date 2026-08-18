package com.chatapplication.group_chat.dto.response;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TypingResponse {
    /**
     * User who is typing
     */
    private Long userId;

    /**
     * User Name
     */
    private String userName;

    /**
     * User Profile Picture
     */
    private String profilePicture;

    /**
     * Receiver ID
     * (Available for private chat)
     */
    private Long receiverId;

    /**
     * Group ID
     * (Available for group chat)
     */
    private Long groupId;

    /**
     * Typing Status
     */
    private Boolean typing;

    /**
     * Event Time
     */
    private LocalDateTime timestamp;
}
