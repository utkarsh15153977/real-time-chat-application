package com.chatapplication.group_chat.dto.response;

import com.chatapplication.group_chat.entitty.AttachmentType;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttachmentResponse {
    /**
     * Attachment ID
     */
    private Long attachmentId;

    /**
     * Original File Name
     */
    private String fileName;

    /**
     * Stored File Name
     */
    private String storedFileName;

    /**
     * File URL
     */
    private String fileUrl;

    /**
     * Thumbnail URL (for images/videos)
     */
    private String thumbnailUrl;

    /**
     * MIME Type
     */
    private String contentType;

    /**
     * IMAGE / VIDEO / AUDIO / DOCUMENT / PDF
     */
    private AttachmentType attachmentType;

    /**
     * File Size (Bytes)
     */
    private Long fileSize;

    /**
     * Image/Video Width
     */
    private Integer width;

    /**
     * Image/Video Height
     */
    private Integer height;

    /**
     * Duration (Audio/Video in seconds)
     */
    private Long duration;

    /**
     * Upload Status
     */
    private Boolean uploaded;

    /**
     * Upload Time
     */
    private LocalDateTime uploadedAt;
}
