package com.chatapplication.group_chat.entitty;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
        name = "chat_groups",
        indexes = {
                @Index(name = "idx_group_name", columnList = "name"),
                @Index(name = "idx_created_by", columnList = "created_by")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Group {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "group_id")
    private Long id;

    /**
     * Group Name
     */
    @NotBlank
    @Size(max = 100)
    @Column(name = "name", nullable = false, length = 100)
    private String name;

    /**
     * Group Description
     */
    @Size(max = 500)
    @Column(name = "description", length = 500)
    private String description;

    /**
     * Group Profile Image URL
     */
    @Column(name = "group_image")
    private String groupImage;

    /**
     * Created By (User Service)
     */
    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    /**
     * Total Members
     */
    @Builder.Default
    @Column(name = "member_count")
    private Integer memberCount = 1;

    /**
     * Last Message in Group
     */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "last_message_id")
    private GroupMessage lastMessage;

    /**
     * Group Members
     */
    @Builder.Default
    @OneToMany(
            mappedBy = "group",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    private List<GroupMember> members = new ArrayList<>();

    /**
     * Group Messages
     */
    @Builder.Default
    @OneToMany(
            mappedBy = "group",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    @OrderBy("createdAt ASC")
    private List<GroupMessage> messages = new ArrayList<>();

    /**
     * Group Active Status
     */
    @Builder.Default
    @Column(name = "active")
    private boolean active = true;

    /**
     * Soft Delete
     */
    @Builder.Default
    @Column(name = "deleted")
    private boolean deleted = false;

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

        if (memberCount == null) {
            memberCount = 1;
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * Add Member
     */
    public void addMember(GroupMember member) {
        members.add(member);
        member.setGroup(this);
        memberCount = members.size();
    }

    /**
     * Remove Member
     */
    public void removeMember(GroupMember member) {
        members.remove(member);
        member.setGroup(null);
        memberCount = members.size();
    }

    /**
     * Add Group Message
     */
    public void addMessage(GroupMessage message) {
        messages.add(message);
        message.setGroup(this);
        lastMessage = message;
        updatedAt = LocalDateTime.now();
    }

    /**
     * Remove Group Message
     */
    public void removeMessage(GroupMessage message) {
        messages.remove(message);
        message.setGroup(null);
    }
}
