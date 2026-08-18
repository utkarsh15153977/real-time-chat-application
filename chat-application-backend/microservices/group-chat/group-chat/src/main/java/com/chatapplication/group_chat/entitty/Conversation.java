package com.chatapplication.group_chat.entitty;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
        name = "conversations",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_conversation_users",
                        columnNames = {"user1_id", "user2_id"}
                )
        },
        indexes = {
                @Index(name = "idx_user1", columnList = "user1_id"),
                @Index(name = "idx_user2", columnList = "user2_id"),
                @Index(name = "idx_updated_at", columnList = "updated_at")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Conversation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "conversation_id")
    private Long id;

    /**
     * First participant (User Service)
     */
    @Column(name = "user1_id", nullable = false)
    private Long user1Id;

    /**
     * Second participant (User Service)
     */
    @Column(name = "user2_id", nullable = false)
    private Long user2Id;

    /**
     * Last message in conversation.
     */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "last_message_id")
    private ChatMessage lastMessage;

    /**
     * Total unread messages for User1.
     */
    @Builder.Default
    @Column(name = "user1_unread_count")
    private Integer user1UnreadCount = 0;

    /**
     * Total unread messages for User2.
     */
    @Builder.Default
    @Column(name = "user2_unread_count")
    private Integer user2UnreadCount = 0;

    /**
     * All messages belonging to this conversation.
     */
    @OneToMany(
            mappedBy = "conversation",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    @OrderBy("createdAt ASC")
    @Builder.Default
    private List<ChatMessage> messages = new ArrayList<>();

    /**
     * Whether conversation is archived.
     */
    @Builder.Default
    @Column(name = "archived")
    private boolean archived = false;

    /**
     * Whether conversation is deleted.
     */
    @Builder.Default
    @Column(name = "deleted")
    private boolean deleted = false;

    /**
     * Creation timestamp.
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Last activity timestamp.
     */
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();

        if (user1UnreadCount == null) {
            user1UnreadCount = 0;
        }

        if (user2UnreadCount == null) {
            user2UnreadCount = 0;
        }
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Utility method to add a message.
     */
    public void addMessage(ChatMessage message) {
        messages.add(message);
        message.setConversation(this);
        this.lastMessage = message;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Utility method to remove a message.
     */
    public void removeMessage(ChatMessage message) {
        messages.remove(message);
        message.setConversation(null);
    }
}
