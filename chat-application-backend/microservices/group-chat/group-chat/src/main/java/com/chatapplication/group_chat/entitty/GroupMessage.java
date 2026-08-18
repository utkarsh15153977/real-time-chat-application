package com.chatapplication.group_chat.entitty;

import jakarta.persistence.*;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "group_messages",
        indexes = {
                @Index(name = "idx_group_message_group", columnList = "group_id"),
                @Index(name = "idx_group_message_sender", columnList = "sender_id"),
                @Index(name = "idx_group_message_created", columnList = "created_at")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupMessage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "group_message_id")
    private Long id;

    /**
     * Group
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", nullable = false)
    private Group group;

    /**
     * Sender (User Service)
     */
    @Column(name = "sender_id", nullable = false)
    private Long senderId;

    /**
     * Message Content
     */
    @Size(max = 5000)
    @Column(name = "content", length = 5000)
    private String content;

    /**
     * TEXT / IMAGE / VIDEO / AUDIO / DOCUMENT
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "message_type", nullable = false)
    @Builder.Default
    private MessageType messageType = MessageType.TEXT;

    /**
     * SENT / DELIVERED / SEEN
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private MessageStatus status = MessageStatus.SENT;

    /**
     * Reply to another group message
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reply_to_message_id")
    private GroupMessage replyTo;

    /**
     * Attachment
     */
    @OneToOne(
            mappedBy = "groupMessage",
            cascade = CascadeType.ALL,
            fetch = FetchType.LAZY,
            orphanRemoval = true
    )
    private Attachment attachment;

    /**
     * Reactions
     */
    @OneToMany(
            mappedBy = "groupMessage",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    @Builder.Default
    private List<MessageReaction> reactions = new ArrayList<>();

    /**
     * Forwarded Message
     */
    @Builder.Default
    @Column(name = "forwarded")
    private Boolean forwarded = false;

    /**
     * Edited Message
     */
    @Builder.Default
    @Column(name = "edited")
    private Boolean edited = false;

    /**
     * Soft Delete
     */
    @Builder.Default
    @Column(name = "deleted")
    private Boolean deleted = false;

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

        if (messageType == null) {
            messageType = MessageType.TEXT;
        }

        if (status == null) {
            status = MessageStatus.SENT;
        }

        if (forwarded == null) {
            forwarded = false;
        }

        if (edited == null) {
            edited = false;
        }

        if (deleted == null) {
            deleted = false;
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * Add Reaction
     */
    public void addReaction(MessageReaction reaction) {
        reactions.add(reaction);
        reaction.setGroupMessage(this);
    }

    /**
     * Remove Reaction
     */
    public void removeReaction(MessageReaction reaction) {
        reactions.remove(reaction);
        reaction.setGroupMessage(null);
    }
}
