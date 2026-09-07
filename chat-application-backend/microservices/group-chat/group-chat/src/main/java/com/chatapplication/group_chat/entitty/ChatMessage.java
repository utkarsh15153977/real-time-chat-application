package com.chatapplication.group_chat.entitty;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "chat_messages",
        indexes = {
                @Index(name = "idx_sender", columnList = "sender_id"),
                @Index(name = "idx_receiver", columnList = "receiver_id"),
                @Index(name = "idx_conversation", columnList = "conversation_id"),
                @Index(name = "idx_timestamp", columnList = "created_at")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "message_id")
    private Long id;

    /**
     * Conversation to which this message belongs.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conversation_id", nullable = false)
    private Conversation conversation;

    /**
     * Sender User Id (User Service)
     */
    @NotNull
    @Column(name = "sender_id", nullable = false)
    private Long senderId;

    /**
     * Receiver User Id (User Service)
     */
    @NotNull
    @Column(name = "receiver_id", nullable = false)
    private Long receiverId;

    /**
     * Text message.
     */
    @Size(max = 5000)
    @Column(name = "content", length = 5000)
    private String content;

    /**
     * TEXT / IMAGE / VIDEO / AUDIO / DOCUMENT
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "message_type", nullable = false)
    private MessageType messageType;

    /**
     * SENT / DELIVERED / SEEN
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private MessageStatus status;

    /**
     * If message contains attachment.
     */
    @OneToOne(
            mappedBy = "chatMessage",
            cascade = CascadeType.ALL,
            fetch = FetchType.LAZY
    )
    private Attachment attachment;

    /**
     * Reply feature.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reply_to_message")
    private ChatMessage replyTo;

    /**
     * Forwarded message.
     */
    @Column(name = "forwarded")
    private boolean forwarded;

    /**
     * Edited message.
     */
    @Column(name = "edited")
    private boolean edited;

    /**
     * Deleted (Soft Delete)
     */
    @Column(name = "deleted")
    private boolean deleted;

    /**
     * Delivery Time
     */
    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;

    /**
     * Seen Time
     */
    @Column(name = "seen_at")
    private LocalDateTime seenAt;

    /**
     * Creation Time
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Last Updated
     */
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    public void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();

        if (this.status == null) {
            this.status = MessageStatus.SENT;
        }

        if (this.messageType == null) {
            this.messageType = MessageType.TEXT;
        }
    }

    @Column(name = "edited_at")
    private LocalDateTime editedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
