package com.chatapplication.group_chat.repository;

import com.chatapplication.group_chat.entitty.Group;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.awt.print.Pageable;
import java.util.List;
import java.util.Optional;

public interface GroupRepository extends JpaRepository<Group, Long> {
    /**
     * Find group by id
     */
    Optional<Group> findByIdAndDeletedFalse(Long id);

    /**
     * Find all active groups
     */
    List<Group> findByDeletedFalseOrderByUpdatedAtDesc();

    /**
     * Find all active groups with pagination
     */
    List<Group> findByDeletedFalseOrderByUpdatedAtDesc(Pageable pageable);

    /**
     * Find groups created by user
     */
    List<Group> findByCreatedByAndDeletedFalseOrderByCreatedAtDesc(Long createdBy);

    /**
     * Search groups by name
     */
    @Query("""
            SELECT g
            FROM Group g
            WHERE LOWER(g.name)
            LIKE LOWER(CONCAT('%', :keyword, '%'))
            AND g.deleted = false
            ORDER BY g.updatedAt DESC
            """)
    List<Group> searchGroups(@Param("keyword") String keyword);

    /**
     * Search groups by name with pagination
     */
    @Query("""
            SELECT g
            FROM Group g
            WHERE LOWER(g.name)
            LIKE LOWER(CONCAT('%', :keyword, '%'))
            AND g.deleted = false
            ORDER BY g.updatedAt DESC
            """)
    List<Group> searchGroups(
            @Param("keyword") String keyword,
            Pageable pageable
    );

    /**
     * Find groups in which user is a member
     */
    @Query("""
            SELECT DISTINCT g
            FROM Group g
            JOIN g.members gm
            WHERE gm.userId = :userId
            AND gm.active = true
            AND g.deleted = false
            ORDER BY g.updatedAt DESC
            """)
    List<Group> findGroupsByMember(@Param("userId") Long userId);

    /**
     * Find groups in which user is a member (Paginated)
     */
    @Query("""
            SELECT DISTINCT g
            FROM Group g
            JOIN g.members gm
            WHERE gm.userId = :userId
            AND gm.active = true
            AND g.deleted = false
            ORDER BY g.updatedAt DESC
            """)
    List<Group> findGroupsByMember(
            @Param("userId") Long userId,
            Pageable pageable
    );

    /**
     * Count groups created by user
     */
    long countByCreatedByAndDeletedFalse(Long createdBy);

    /**
     * Count all active groups
     */
    long countByDeletedFalse();

    /**
     * Check if group exists
     */
    boolean existsByIdAndDeletedFalse(Long id);

    /**
     * Soft delete group
     */
    @Modifying
    @Query("""
            UPDATE Group g
            SET g.deleted = true,
                g.active = false
            WHERE g.id = :groupId
            """)
    int softDelete(@Param("groupId") Long groupId);

    /**
     * Activate group
     */
    @Modifying
    @Query("""
            UPDATE Group g
            SET g.active = true
            WHERE g.id = :groupId
            """)
    int activateGroup(@Param("groupId") Long groupId);

    /**
     * Deactivate group
     */
    @Modifying
    @Query("""
            UPDATE Group g
            SET g.active = false
            WHERE g.id = :groupId
            """)
    int deactivateGroup(@Param("groupId") Long groupId);

    /**
     * Find group by name
     */
    Optional<Group> findByNameIgnoreCaseAndDeletedFalse(String name);

    /**
     * Check duplicate group name
     */
    boolean existsByNameIgnoreCaseAndDeletedFalse(String name);

    /**
     * Find active groups
     */
    List<Group> findByActiveTrueAndDeletedFalse();

    /**
     * Count active groups
     */
    long countByActiveTrueAndDeletedFalse();

    /**
     * Find groups having more than given members
     */
    List<Group> findByMemberCountGreaterThanAndDeletedFalse(Integer memberCount);
}
