package com.chatapplication.group_chat.repository;

import com.chatapplication.group_chat.entitty.PresenceStatus;
import com.chatapplication.group_chat.entitty.UserPresence;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface UserPresenceRepository extends JpaRepository<UserPresence, Long> {
    /**
     * Find presence by user id
     */
    Optional<UserPresence> findByUserId(Long userId);

    /**
     * Check user exists
     */
    boolean existsByUserId(Long userId);

    /**
     * Find online users
     */
    List<UserPresence> findByOnlineTrue();

    /**
     * Find offline users
     */
    List<UserPresence> findByOnlineFalse();

    /**
     * Find users by status
     */
    List<UserPresence> findByStatus(PresenceStatus status);

    /**
     * Find by session id
     */
    Optional<UserPresence> findBySessionId(String sessionId);

    /**
     * Find by device type
     */
    List<UserPresence> findByDeviceType(String deviceType);

    /**
     * Count online users
     */
    long countByOnlineTrue();

    /**
     * Count offline users
     */
    long countByOnlineFalse();

    /**
     * Count users by status
     */
    long countByStatus(PresenceStatus status);

    /**
     * Find users active after a given time
     */
    List<UserPresence> findByLastLoginAfter(
            LocalDateTime dateTime
    );

    /**
     * Find users last seen before a given time
     */
    List<UserPresence> findByLastSeenBefore(
            LocalDateTime dateTime
    );

    /**
     * Mark user online
     */
    @Modifying
    @Query("""
            UPDATE UserPresence up
            SET up.online = true,
                up.status = 'ONLINE',
                up.sessionId = :sessionId,
                up.lastLogin = CURRENT_TIMESTAMP
            WHERE up.userId = :userId
            """)
    int markOnline(
            @Param("userId") Long userId,
            @Param("sessionId") String sessionId
    );

    /**
     * Mark user offline
     */
    @Modifying
    @Query("""
            UPDATE UserPresence up
            SET up.online = false,
                up.status = 'OFFLINE',
                up.sessionId = null,
                up.lastSeen = CURRENT_TIMESTAMP
            WHERE up.userId = :userId
            """)
    int markOffline(
            @Param("userId") Long userId
    );

    /**
     * Update device type
     */
    @Modifying
    @Query("""
            UPDATE UserPresence up
            SET up.deviceType = :deviceType
            WHERE up.userId = :userId
            """)
    int updateDeviceType(
            @Param("userId") Long userId,
            @Param("deviceType") String deviceType
    );

    /**
     * Delete presence by user
     */
    void deleteByUserId(Long userId);
}
