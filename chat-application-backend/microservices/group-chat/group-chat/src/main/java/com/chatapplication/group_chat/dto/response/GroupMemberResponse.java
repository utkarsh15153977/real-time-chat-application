package com.chatapplication.group_chat.dto.response;

import com.chatapplication.group_chat.entitty.GroupRole;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupMemberResponse {
    /**
     * User ID
     */
    private Long userId;

    /**
     * Member Name
     */
    private String userName;

    /**
     * Profile Picture
     */
    private String profilePicture;

    /**
     * Member Role
     */
    private GroupRole role;

    /**
     * Online Status
     */
    private Boolean online;

    /**
     * Last Seen
     */
    private LocalDateTime lastSeen;

    /**
     * Joined Date
     */
    private LocalDateTime joinedAt;
}
