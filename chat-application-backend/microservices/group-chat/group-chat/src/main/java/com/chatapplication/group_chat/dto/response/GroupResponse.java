package com.chatapplication.group_chat.dto.response;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupResponse {
    /**
     * Group ID
     */
    private Long groupId;

    /**
     * Group Name
     */
    private String groupName;

    /**
     * Group Description
     */
    private String description;

    /**
     * Group Profile Image
     */
    private String groupImage;

    /**
     * Group Creator
     */
    private Long createdBy;

    private String creatorName;

    /**
     * Group Creation Time
     */
    private LocalDateTime createdAt;

    /**
     * Number of Members
     */
    private Integer memberCount;

    /**
     * Members
     */
    private List<GroupMemberResponse> members;

    /**
     * Last Message Information
     */
    private Long lastMessageId;

    private String lastMessage;

    private String lastMessageSender;

    private LocalDateTime lastMessageTime;

    /**
     * Unread Messages
     */
    private Long unreadCount;

    /**
     * Group Settings
     */
    private Boolean adminOnlyMessages;

    private Boolean membersCanEditInfo;

    private Boolean membersCanInvite;

    /**
     * Current User Role
     */
    private String currentUserRole;

    /**
     * Group Status
     */
    private Boolean active;
}
