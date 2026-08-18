package com.chatapplication.group_chat.entitty;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "user_presence",
        indexes = {
                @Index(name = "idx_presence_user", columnList = "user_id"),
                @Index(name = "idx_presence_status", columnList = "status"),
                @Index(name = "idx_presence_last_seen", columnList = "last_seen")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserPresence {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "presence_id")
    private Long id;

    /**
     * User ID from User Service
     */
    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    /**
     * ONLINE / OFFLINE / AWAY / BUSY
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private PresenceStatus status = PresenceStatus.OFFLINE;

    /**
     * Is user currently online
     */
    @Builder.Default
    @Column(name = "online")
    private Boolean online = false;

    /**
     * Last seen timestamp
     */
    @Column(name = "last_seen")
    private LocalDateTime lastSeen;

    /**
     * Last login time
     */
    @Column(name = "last_login")
    private LocalDateTime lastLogin;

    /**
     * Current WebSocket session ID
     */
    @Column(name = "session_id")
    private String sessionId;

    /**
     * Device information (Android, iOS, Web, Desktop)
     */
    @Column(name = "device_type", length = 30)
    private String deviceType;

    /**
     * IP Address
     */
    @Column(name = "ip_address", length = 50)
    private String ipAddress;

    /**
     * Created timestamp
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Updated timestamp
     */
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {

        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();

        if (status == null) {
            status = PresenceStatus.OFFLINE;
        }

        if (online == null) {
            online = false;
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * Mark user online
     */
    public void markOnline(String sessionId, String deviceType) {
        this.online = true;
        this.status = PresenceStatus.ONLINE;
        this.sessionId = sessionId;
        this.deviceType = deviceType;
        this.lastLogin = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Mark user offline
     */
    public void markOffline() {
        this.online = false;
        this.status = PresenceStatus.OFFLINE;
        this.lastSeen = LocalDateTime.now();
        this.sessionId = null;
        this.updatedAt = LocalDateTime.now();
    }
}
