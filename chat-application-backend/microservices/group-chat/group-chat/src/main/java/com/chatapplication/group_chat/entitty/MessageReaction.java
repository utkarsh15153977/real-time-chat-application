package com.chatapplication.group_chat.entitty;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "message_reactions",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_chat_message_reaction",
                        columnNames = {"chat_message_id", "user_id"}
                ),
                @UniqueConstraint(
                        name = "uk_group_message_reaction",
                        columnNames = {"group_message_id", "user_id"}
                )
        },
        indexes = {
                @Index(name = "idx_reaction_chat_message", columnList = "chat_message_id"),
                @Index(name = "idx_reaction_group_message", columnList = "group_message_id"),
                @Index(name = "idx_reaction_user", columnList = "user_id")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageReaction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "reaction_id")
    private Long id;

    /**
     * Private Chat Message
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chat_message_id")
    private ChatMessage chatMessage;

    /**
     * Group Chat Message
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_message_id")
    private GroupMessage groupMessage;

    /**
     * User reacting to the message
     * (User Service)
     */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /**
     * 👍 ❤️ 😂 😮 😢 🔥 etc.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "reaction_type", nullable = false, length = 30)
    private ReactionType reactionType;

    /**
     * Created Timestamp
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Updated Timestamp
     */
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {

        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();

        if (reactionType == null) {
            reactionType = ReactionType.LIKE;
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
