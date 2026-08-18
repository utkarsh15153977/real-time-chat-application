package com.chatapplication.group_chat.dto.response;

import com.chatapplication.group_chat.entitty.PresenceStatus;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PresenceResponse {
    /**
     * User ID
     */
    private Long userId;

    /**
     * User Name
     */
    private String userName;

    /**
     * Profile Picture
     */
    private String profilePicture;

    /**
     * ONLINE / OFFLINE / AWAY / BUSY
     */
    private PresenceStatus status;

    /**
     * Is User Online
     */
    private Boolean online;

    /**
     * Last Login Time
     */
    private LocalDateTime lastLogin;

    /**
     * Last Seen Time
     */
    private LocalDateTime lastSeen;

    /**
     * Last Activity Time
     */
    private LocalDateTime lastActivity;

    /**
     * User is currently typing
     */
    private Boolean typing;

    /**
     * Device Type
     * MOBILE / WEB / DESKTOP
     */
    private String deviceType;

    /**
     * Current Session ID
     */
    private String sessionId;
}
