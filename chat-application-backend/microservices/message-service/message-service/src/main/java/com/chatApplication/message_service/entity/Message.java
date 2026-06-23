package com.chatApplication.message_service.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

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