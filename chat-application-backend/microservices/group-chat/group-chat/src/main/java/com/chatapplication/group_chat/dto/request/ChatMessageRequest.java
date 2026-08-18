package com.chatapplication.group_chat.dto.request;

import com.chatapplication.group_chat.entitty.MessageType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatMessageRequest {
    /**
     * Sender User ID
     */
    @NotNull(message = "Sender Id is required")
    private Long senderId;

    /**
     * Receiver User ID
     * (Null for Group Chat)
     */
    private Long receiverId;

    /**
     * Group ID
     * (Null for Private Chat)
     */
    private Long groupId;

    /**
     * Conversation ID
     * (Optional - if already exists)
     */
    private Long conversationId;

    /**
     * Message Content
     */
    @Size(max = 5000, message = "Message cannot exceed 5000 characters")
    private String content;

    /**
     * TEXT / IMAGE / VIDEO / AUDIO / DOCUMENT
     */
    @Builder.Default
    private MessageType messageType = MessageType.TEXT;

    /**
     * Reply Message ID
     */
    private Long replyToMessageId;

    /**
     * Attachment ID
     */
    private Long attachmentId;

    /**
     * Forwarded Message
     */
    @Builder.Default
    private Boolean forwarded = false;
}
