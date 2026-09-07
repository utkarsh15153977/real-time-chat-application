package com.chatApplication.message_service.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Message entity representing a single chat message.
 * <p>
 * Supports both text and media messages. Media fields (mediaUrl, fileKey,
 * fileSizeBytes) are populated only when messageType is not TEXT.
 * <p>
 * The entity maps to the 'message_table' which is managed by both
 * Hibernate auto-DDL and Flyway migrations for schema consistency.
 */
@Entity
@Table(name = "message_table")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long msgId;

    private String senderId;

    private String receiverId;

    @Column(length = 5000)
    private String content;

    @Column(nullable = false, updatable = false)
    private LocalDateTime timestamp;

    @PrePersist
    public void prePersist() {
        this.timestamp = LocalDateTime.now();
    }

    @Enumerated(EnumType.STRING)
    private MessageStatus status;

    /** Type of message content (TEXT, IMAGE, VIDEO, AUDIO, FILE) */
    @Enumerated(EnumType.STRING)
    @Column(name = "message_type", nullable = false, length = 30)
    @Builder.Default
    private MessageType messageType = MessageType.TEXT;

    /** Public S3 URL of the uploaded media file */
    @Column(name = "media_url", columnDefinition = "TEXT")
    private String mediaUrl;

    /** S3 object key for the uploaded file (used for deletion/reference) */
    @Column(name = "file_key", length = 500)
    private String fileKey;

    /** Size of the uploaded file in bytes */
    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    // Legacy attachment fields (kept for backward compatibility)
    @Column(name = "attachment_url")
    private String attachmentUrl;

    @Column(name = "attachment_type")
    private String attachmentType;

    @Column(name = "attachment_name")
    private String attachmentName;

    @Column(name = "attachment_size")
    private Long attachmentSize;

    @Column(name = "is_attachment")
    private boolean isAttachment;
}
