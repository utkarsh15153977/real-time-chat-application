package com.chatbackend.chat_application_backend.service;

import com.chatbackend.chat_application_backend.dto.GroupDTOs.GroupDetailResponse;
import com.chatbackend.chat_application_backend.dto.GroupDTOs.GroupMemberResponse;
import com.chatbackend.chat_application_backend.entity.ChatRoom;
import com.chatbackend.chat_application_backend.entity.GroupRole;

import java.util.List;

public interface GroupService {

    ChatRoom createGroup(String name, String groupIconUrl, List<Long> initialMemberIds, Long creatorId);

    GroupDetailResponse getGroupDetails(Long chatRoomId, Long requesterId);

    void addMember(Long chatRoomId, Long targetUserId, Long actorId);

    void removeMember(Long chatRoomId, Long targetUserId, Long actorId);

    void updateMemberRole(Long chatRoomId, Long targetUserId, GroupRole newRole, Long actorId);

    String generateInviteCode(Long chatRoomId, Long actorId);

    ChatRoom joinByInviteCode(String inviteCode, Long userId);

    List<GroupMemberResponse> getGroupMembers(Long chatRoomId);
}
