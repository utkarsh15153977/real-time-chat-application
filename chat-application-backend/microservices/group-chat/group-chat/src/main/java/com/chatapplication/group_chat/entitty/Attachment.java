package com.chatapplication.group_chat.entitty;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "attachments",
        indexes = {
                @Index(name = "idx_attachment_type", columnList = "attachment_type"),
                @Index(name = "idx_attachment_chat", columnList = "chat_message_id"),
                @Index(name = "idx_attachment_group", columnList = "group_message_id")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Attachment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "attachment_id")
    private Long id;

    /**
     * Private Chat Message
     */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chat_message_id")
    private ChatMessage chatMessage;

    /**
     * Group Chat Message
     */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_message_id")
    private GroupMessage groupMessage;

    /**
     * Original File Name
     */
    @Column(name = "file_name", nullable = false)
    private String fileName;

    /**
     * Stored File Name (UUID)
     */
    @Column(name = "stored_file_name", nullable = false, unique = true)
    private String storedFileName;

    /**
     * File URL (S3 / MinIO / Local)
     */
    @Column(name = "file_url", nullable = false, length = 1000)
    private String fileUrl;

    /**
     * MIME Type
     */
    @Column(name = "content_type")
    private String contentType;

    /**
     * IMAGE / VIDEO / AUDIO / DOCUMENT
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "attachment_type", nullable = false)
    private AttachmentType attachmentType;

    /**
     * File Size (Bytes)
     */
    @Column(name = "file_size")
    private Long fileSize;

    /**
     * Image Width
     */
    @Column(name = "width")
    private Integer width;

    /**
     * Image Height
     */
    @Column(name = "height")
    private Integer height;

    /**
     * Video/Audio Duration (Seconds)
     */
    @Column(name = "duration")
    private Integer duration;

    /**
     * Thumbnail URL
     */
    @Column(name = "thumbnail_url", length = 1000)
    private String thumbnailUrl;

    /**
     * Upload Status
     */
    @Builder.Default
    @Column(name = "uploaded")
    private Boolean uploaded = true;

    /**
     * Created Time
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Updated Time
     */
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {

        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();

        if (uploaded == null) {
            uploaded = true;
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
