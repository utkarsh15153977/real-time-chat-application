package com.chatapplication.group_chat.repository;

import com.chatapplication.group_chat.entitty.Group;
import com.chatapplication.group_chat.entitty.GroupMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.chatapplication.group_chat.entitty.GroupRole;
import java.util.List;
import java.util.Optional;

public interface GroupMemberRepository extends JpaRepository<GroupMember, Long> {

    Optional<GroupMember> findByGroupAndUserId(
            Group group,
            Long userId
    );

    Optional<GroupMember> findByGroupAndUserIdAndActiveTrue(
            Group group,
            Long userId
    );

    List<GroupMember> findByGroupAndActiveTrueOrderByJoinedAtAsc(
            Group group
    );

    List<GroupMember> findByGroupOrderByJoinedAtAsc(
            Group group
    );

    long countByGroupAndActiveTrue(
            Group group
    );

    boolean existsByGroupAndUserIdAndActiveTrue(
            Group group,
            Long userId
    );

    List<GroupMember> findByGroupAndMutedTrue(
            Group group
    );

    List<GroupMember> findByRole(
            GroupRole role
    );

    List<GroupMember> findByUserIdAndActiveTrueOrderByJoinedAtDesc(
            Long userId
    );

    @Modifying
    @Query("""
            UPDATE GroupMember gm
            SET gm.role = :role
            WHERE gm.group = :group
            AND gm.userId = :userId
            """)
    int updateRole(
            @Param("group") Group group,
            @Param("userId") Long userId,
            @Param("role") GroupRole role
    );

    @Modifying
    @Query("""
            UPDATE GroupMember gm
            SET gm.muted = true
            WHERE gm.group = :group
            AND gm.userId = :userId
            """)
    int muteMember(
            @Param("group") Group group,
            @Param("userId") Long userId
    );

    @Modifying
    @Query("""
            UPDATE GroupMember gm
            SET gm.muted = false
            WHERE gm.group = :group
            AND gm.userId = :userId
            """)
    int unMuteMember(
            @Param("group") Group group,
            @Param("userId") Long userId
    );

    @Modifying
    @Query("""
            UPDATE GroupMember gm
            SET gm.active = false
            WHERE gm.group = :group
            AND gm.userId = :userId
            """)
    int leaveGroup(
            @Param("group") Group group,
            @Param("userId") Long userId
    );

    @Modifying
    @Query("""
            DELETE FROM GroupMember gm
            WHERE gm.group = :group
            AND gm.userId = :userId
            """)
    int removeMember(
            @Param("group") Group group,
            @Param("userId") Long userId
    );

    @Modifying
    void deleteByGroup(
            Group group
    );

    long countByGroupAndRoleAndActiveTrue(
            Group group,
            GroupRole role
    );

    Optional<GroupMember> findByGroupAndRole(
            Group group,
            GroupRole role
    );

    List<GroupMember> findByGroupAndRoleAndActiveTrue(
            Group group,
            GroupRole role
    );

    boolean existsByGroupAndUserIdAndRoleAndActiveTrue(
            Group group,
            Long userId,
            GroupRole role
    );
}