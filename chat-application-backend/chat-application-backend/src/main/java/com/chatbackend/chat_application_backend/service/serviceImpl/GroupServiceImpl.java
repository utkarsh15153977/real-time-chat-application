package com.chatbackend.chat_application_backend.service.serviceImpl;

import com.chatbackend.chat_application_backend.dto.GroupDTOs;
import com.chatbackend.chat_application_backend.dto.GroupDTOs.GroupDetailResponse;
import com.chatbackend.chat_application_backend.dto.GroupDTOs.GroupEventNotification;
import com.chatbackend.chat_application_backend.dto.GroupDTOs.GroupMemberResponse;
import com.chatbackend.chat_application_backend.entity.ChatRoom;
import com.chatbackend.chat_application_backend.entity.GroupMember;
import com.chatbackend.chat_application_backend.entity.GroupRole;
import com.chatbackend.chat_application_backend.entity.User;
import com.chatbackend.chat_application_backend.exception.ChatNotFoundException;
import com.chatbackend.chat_application_backend.exception.UserNotFoundException;
import com.chatbackend.chat_application_backend.repository.ChatRepository;
import com.chatbackend.chat_application_backend.repository.GroupMemberRepository;
import com.chatbackend.chat_application_backend.repository.UserRepository;
import com.chatbackend.chat_application_backend.service.GroupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class GroupServiceImpl implements GroupService {

    private final ChatRepository chatRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;

    private static final String INVITE_CODE_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final int INVITE_CODE_LENGTH = 8;
    private final SecureRandom secureRandom = new SecureRandom();

    private User getUserOrThrow(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found with id: " + userId));
    }

    private ChatRoom getChatRoomOrThrow(Long chatRoomId) {
        return chatRepository.findById(chatRoomId)
                .orElseThrow(() -> new ChatNotFoundException("Chat room not found with id: " + chatRoomId));
    }

    private GroupMember getGroupMemberOrThrow(Long chatRoomId, Long userId) {
        return groupMemberRepository.findByChatRoomIdAndUserId(chatRoomId, userId)
                .orElseThrow(() -> new AccessDeniedException("User is not a member of this group"));
    }

    private void validateAdmin(Long chatRoomId, Long actorId) {
        GroupMember actor = getGroupMemberOrThrow(chatRoomId, actorId);
        if (actor.getRole() != GroupRole.ADMIN) {
            throw new AccessDeniedException("Only group admins can perform this action");
        }
    }

    private void sendGroupEvent(Long chatRoomId, String eventType, Long actorId, Long targetUserId) {
        GroupEventNotification notification = new GroupEventNotification(
                eventType, chatRoomId, actorId, targetUserId);
        messagingTemplate.convertAndSend(
                "/topic/chat/" + chatRoomId + "/group-events", notification);
    }

    @Override
    @Transactional
    public ChatRoom createGroup(String name, String groupIconUrl, List<Long> initialMemberIds, Long creatorId) {
        User creator = getUserOrThrow(creatorId);

        ChatRoom chatRoom = ChatRoom.builder()
                .groupChat(true)
                .groupName(name)
                .groupIconUrl(groupIconUrl)
                .participants(new ArrayList<>())
                .members(new ArrayList<>())
                .build();
        chatRoom = chatRepository.save(chatRoom);

        GroupMember adminMember = GroupMember.builder()
                .chatRoom(chatRoom)
                .user(creator)
                .role(GroupRole.ADMIN)
                .build();
        groupMemberRepository.save(adminMember);
        chatRoom.getParticipants().add(creator);

        if (initialMemberIds != null) {
            for (Long memberId : initialMemberIds) {
                if (!memberId.equals(creatorId)) {
                    User member = getUserOrThrow(memberId);
                    GroupMember groupMember = GroupMember.builder()
                            .chatRoom(chatRoom)
                            .user(member)
                            .role(GroupRole.MEMBER)
                            .build();
                    groupMemberRepository.save(groupMember);
                    chatRoom.getParticipants().add(member);
                }
            }
        }

        chatRoom = chatRepository.save(chatRoom);
        sendGroupEvent(chatRoom.getId(), "GROUP_CREATED", creatorId, null);
        return chatRoom;
    }

    @Override
    @Transactional(readOnly = true)
    public GroupDetailResponse getGroupDetails(Long chatRoomId, Long requesterId) {
        ChatRoom chatRoom = getChatRoomOrThrow(chatRoomId);
        getGroupMemberOrThrow(chatRoomId, requesterId);

        List<GroupMember> members = groupMemberRepository.findByChatRoomId(chatRoomId);
        List<GroupMemberResponse> memberResponses = members.stream()
                .map(m -> new GroupMemberResponse(
                        m.getUser().getId(),
                        m.getUser().getName(),
                        m.getUser().getEmail(),
                        m.getRole(),
                        m.getJoinedAt()))
                .collect(Collectors.toList());

        return new GroupDetailResponse(
                chatRoom.getId(),
                chatRoom.getGroupName(),
                chatRoom.getGroupIconUrl(),
                chatRoom.getInviteCode(),
                memberResponses);
    }

    @Override
    @Transactional
    public void addMember(Long chatRoomId, Long targetUserId, Long actorId) {
        validateAdmin(chatRoomId, actorId);
        getChatRoomOrThrow(chatRoomId);
        User targetUser = getUserOrThrow(targetUserId);

        if (groupMemberRepository.existsByChatRoomIdAndUserId(chatRoomId, targetUserId)) {
            throw new IllegalArgumentException("User is already a member of this group");
        }

        ChatRoom chatRoom = getChatRoomOrThrow(chatRoomId);
        GroupMember newMember = GroupMember.builder()
                .chatRoom(chatRoom)
                .user(targetUser)
                .role(GroupRole.MEMBER)
                .build();
        groupMemberRepository.save(newMember);

        if (!chatRoom.getParticipants().stream().anyMatch(u -> u.getId().equals(targetUserId))) {
            chatRoom.getParticipants().add(targetUser);
            chatRepository.save(chatRoom);
        }

        sendGroupEvent(chatRoomId, "MEMBER_ADDED", actorId, targetUserId);
    }

    @Override
    @Transactional
    public void removeMember(Long chatRoomId, Long targetUserId, Long actorId) {
        getChatRoomOrThrow(chatRoomId);
        GroupMember targetMember = getGroupMemberOrThrow(chatRoomId, targetUserId);

        boolean isSelfRemoval = actorId.equals(targetUserId);
        if (!isSelfRemoval) {
            validateAdmin(chatRoomId, actorId);
        }

        if (targetMember.getRole() == GroupRole.ADMIN) {
            long adminCount = groupMemberRepository.countByChatRoomIdAndRole(chatRoomId, GroupRole.ADMIN);
            if (adminCount <= 1) {
                throw new AccessDeniedException("Cannot remove the sole admin of the group");
            }
        }

        groupMemberRepository.deleteByChatRoomIdAndUserId(chatRoomId, targetUserId);

        ChatRoom chatRoom = getChatRoomOrThrow(chatRoomId);
        chatRoom.getParticipants().removeIf(u -> u.getId().equals(targetUserId));
        chatRepository.save(chatRoom);

        sendGroupEvent(chatRoomId, "MEMBER_REMOVED", actorId, targetUserId);
    }

    @Override
    @Transactional
    public void updateMemberRole(Long chatRoomId, Long targetUserId, GroupRole newRole, Long actorId) {
        validateAdmin(chatRoomId, actorId);
        getChatRoomOrThrow(chatRoomId);
        GroupMember targetMember = getGroupMemberOrThrow(chatRoomId, targetUserId);

        if (targetMember.getRole() == GroupRole.ADMIN && newRole != GroupRole.ADMIN) {
            long adminCount = groupMemberRepository.countByChatRoomIdAndRole(chatRoomId, GroupRole.ADMIN);
            if (adminCount <= 1) {
                throw new AccessDeniedException("Cannot demote the sole admin of the group");
            }
        }

        targetMember.setRole(newRole);
        groupMemberRepository.save(targetMember);

        sendGroupEvent(chatRoomId, "ROLE_UPDATED", actorId, targetUserId);
    }

    @Override
    @Transactional
    public String generateInviteCode(Long chatRoomId, Long actorId) {
        validateAdmin(chatRoomId, actorId);
        ChatRoom chatRoom = getChatRoomOrThrow(chatRoomId);

        String inviteCode;
        do {
            inviteCode = generateRandomInviteCode();
        } while (chatRepository.findByInviteCode(inviteCode).isPresent());

        chatRoom.setInviteCode(inviteCode);
        chatRepository.save(chatRoom);

        sendGroupEvent(chatRoomId, "INVITE_CODE_GENERATED", actorId, null);
        return inviteCode;
    }

    @Override
    @Transactional
    public ChatRoom joinByInviteCode(String inviteCode, Long userId) {
        ChatRoom chatRoom = chatRepository.findByInviteCode(inviteCode)
                .orElseThrow(() -> new ChatNotFoundException("Invalid invite code"));

        User user = getUserOrThrow(userId);

        if (groupMemberRepository.existsByChatRoomIdAndUserId(chatRoom.getId(), userId)) {
            throw new IllegalArgumentException("User is already a member of this group");
        }

        GroupMember newMember = GroupMember.builder()
                .chatRoom(chatRoom)
                .user(user)
                .role(GroupRole.MEMBER)
                .build();
        groupMemberRepository.save(newMember);

        if (!chatRoom.getParticipants().stream().anyMatch(u -> u.getId().equals(userId))) {
            chatRoom.getParticipants().add(user);
            chatRepository.save(chatRoom);
        }

        sendGroupEvent(chatRoom.getId(), "MEMBER_JOINED", userId, userId);
        return chatRoom;
    }

    @Override
    @Transactional(readOnly = true)
    public List<GroupMemberResponse> getGroupMembers(Long chatRoomId) {
        getChatRoomOrThrow(chatRoomId);
        List<GroupMember> members = groupMemberRepository.findByChatRoomId(chatRoomId);
        return members.stream()
                .map(m -> new GroupMemberResponse(
                        m.getUser().getId(),
                        m.getUser().getName(),
                        m.getUser().getEmail(),
                        m.getRole(),
                        m.getJoinedAt()))
                .collect(Collectors.toList());
    }

    private String generateRandomInviteCode() {
        StringBuilder sb = new StringBuilder(INVITE_CODE_LENGTH);
        for (int i = 0; i < INVITE_CODE_LENGTH; i++) {
            sb.append(INVITE_CODE_CHARS.charAt(secureRandom.nextInt(INVITE_CODE_CHARS.length())));
        }
        return sb.toString();
    }
}
