package com.chatapplication.group_chat.dto.response;

import com.chatapplication.group_chat.entitty.MessageStatus;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationResponse {
    /**
     * Conversation ID
     */
    private Long conversationId;

    /**
     * Other Participant
     */
    private Long userId;

    private String userName;

    private String profilePicture;

    /**
     * Online Status
     */
    private Boolean online;

    /**
     * Last Seen
     */
    private LocalDateTime lastSeen;

    /**
     * Typing Indicator
     */
    private Boolean typing;

    /**
     * Last Message
     */
    private Long lastMessageId;

    private String lastMessage;

    private String lastMessageSender;

    /**
     * Last Message Type
     */
    private String lastMessageType;

    /**
     * Last Message Status
     */
    private MessageStatus lastMessageStatus;

    /**
     * Last Message Time
     */
    private LocalDateTime lastMessageTime;

    /**
     * Unread Messages
     */
    private Long unreadCount;

    /**
     * Conversation Settings
     */
    private Boolean pinned;

    private Boolean muted;

    private Boolean archived;
}
