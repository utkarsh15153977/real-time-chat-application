package com.chatbackend.chat_application_backend.repository;

import com.chatbackend.chat_application_backend.entity.ChatRoom;
import com.chatbackend.chat_application_backend.entity.GroupMember;
import com.chatbackend.chat_application_backend.entity.GroupRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GroupMemberRepository extends JpaRepository<GroupMember, Long> {

    Optional<GroupMember> findByChatRoomIdAndUserId(Long chatRoomId, Long userId);

    List<GroupMember> findByChatRoomId(Long chatRoomId);

    boolean existsByChatRoomIdAndUserId(Long chatRoomId, Long userId);

    long countByChatRoomIdAndRole(Long chatRoomId, GroupRole role);

    void deleteByChatRoomIdAndUserId(Long chatRoomId, Long userId);
}
