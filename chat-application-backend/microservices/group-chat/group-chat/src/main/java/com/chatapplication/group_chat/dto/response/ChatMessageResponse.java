package com.chatapplication.group_chat.dto.response;

import com.chatapplication.group_chat.entitty.MessageStatus;
import com.chatapplication.group_chat.entitty.MessageType;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageResponse {
    /**
     * Message ID
     */
    private Long messageId;

    /**
     * Conversation ID
     */
    private Long conversationId;

    /**
     * Group ID (null for private chat)
     */
    private Long groupId;

    /**
     * Sender Information
     */
    private Long senderId;

    private String senderName;

    private String senderProfileImage;

    /**
     * Receiver Information
     */
    private Long receiverId;

    private String receiverName;

    /**
     * Message Content
     */
    private String content;

    /**
     * TEXT / IMAGE / VIDEO / AUDIO / DOCUMENT
     */
    private MessageType messageType;

    /**
     * Attachment Details
     */
    private AttachmentResponse attachment;

    /**
     * Reply Information
     */
    private Long replyToMessageId;

    private String replyMessage;

    /**
     * Forwarded Message
     */
    private Boolean forwarded;

    /**
     * Edited Message
     */
    private Boolean edited;

    private LocalDateTime editedAt;

    /**
     * Deleted Message
     */
    private Boolean deleted;

    /**
     * Message Status
     */
    private MessageStatus status;

    /**
     * Reactions
     */
    private List<ReactionResponse> reactions;

    /**
     * Time Information
     */
    private LocalDateTime createdAt;

    private LocalDateTime deliveredAt;

    private LocalDateTime seenAt;
}
