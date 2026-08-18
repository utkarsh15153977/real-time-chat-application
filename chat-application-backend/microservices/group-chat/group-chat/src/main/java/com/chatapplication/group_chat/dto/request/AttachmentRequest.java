package com.chatapplication.group_chat.dto.request;

import com.chatapplication.group_chat.entitty.AttachmentType;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.springframework.web.multipart.MultipartFile;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttachmentRequest {
    /**
     * Sender User ID
     */
    @NotNull(message = "Sender Id is required")
    private Long senderId;

    /**
     * Receiver User ID
     * (Required for private chat)
     */
    private Long receiverId;

    /**
     * Group ID
     * (Required for group chat)
     */
    private Long groupId;

    /**
     * File to upload
     */
    @NotNull(message = "Attachment file is required")
    private MultipartFile file;

    /**
     * IMAGE / VIDEO / AUDIO / DOCUMENT
     */
    @NotNull(message = "Attachment type is required")
    private AttachmentType attachmentType;

    /**
     * Optional caption
     */
    private String caption;

    /**
     * Reply to message ID
     */
    private Long replyToMessageId;
}
