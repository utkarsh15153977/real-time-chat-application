package com.chatapplication.group_chat.entitty;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "group_members",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_group_member",
                        columnNames = {"group_id", "user_id"}
                )
        },
        indexes = {
                @Index(name = "idx_group_member_group", columnList = "group_id"),
                @Index(name = "idx_group_member_user", columnList = "user_id")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupMember {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "group_member_id")
    private Long id;

    /**
     * Group
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", nullable = false)
    private Group group;

    /**
     * User ID from User Service
     */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /**
     * ADMIN / MODERATOR / MEMBER
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    @Builder.Default
    private GroupRole role = GroupRole.MEMBER;

    /**
     * Member nickname inside group
     */
    @Column(name = "nickname", length = 100)
    private String nickname;

    /**
     * Whether notifications are muted
     */
    @Builder.Default
    @Column(name = "muted")
    private Boolean muted = false;

    /**
     * Whether member is active
     */
    @Builder.Default
    @Column(name = "active")
    private Boolean active = true;

    /**
     * Last message read by this member
     */
    @Column(name = "last_read_message_id")
    private Long lastReadMessageId;

    /**
     * Joined timestamp
     */
    @Column(name = "joined_at", nullable = false, updatable = false)
    private LocalDateTime joinedAt;

    /**
     * Last activity timestamp
     */
    @Column(name = "last_active_at")
    private LocalDateTime lastActiveAt;

    @PrePersist
    public void prePersist() {

        joinedAt = LocalDateTime.now();
        lastActiveAt = LocalDateTime.now();

        if (role == null) {
            role = GroupRole.MEMBER;
        }

        if (muted == null) {
            muted = false;
        }

        if (active == null) {
            active = true;
        }
    }

    @PreUpdate
    public void preUpdate() {
        lastActiveAt = LocalDateTime.now();
    }
}
