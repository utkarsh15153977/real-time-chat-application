package com.chatbackend.chat_application_backend.controller;

import com.chatbackend.chat_application_backend.dto.GroupDTOs.*;
import com.chatbackend.chat_application_backend.entity.ChatRoom;
import com.chatbackend.chat_application_backend.entity.GroupRole;
import com.chatbackend.chat_application_backend.entity.User;
import com.chatbackend.chat_application_backend.repository.UserRepository;
import com.chatbackend.chat_application_backend.service.GroupService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/groups")
@RequiredArgsConstructor
public class GroupController {

    private final GroupService groupService;
    private final UserRepository userRepository;

    private Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            throw new AccessDeniedException("Unauthorized");
        }
        User user = userRepository.findByEmail(authentication.getName())
                .or(() -> userRepository.findByPhone(authentication.getName()))
                .orElseThrow(() -> new AccessDeniedException("User not found"));
        return user.getId();
    }

    @PostMapping
    public ResponseEntity<ChatRoom> createGroup(@Valid @RequestBody CreateGroupRequest request) {
        Long currentUserId = getCurrentUserId();
        ChatRoom chatRoom = groupService.createGroup(
                request.getName(),
                request.getGroupIconUrl(),
                request.getInitialMemberIds(),
                currentUserId);
        return ResponseEntity.ok(chatRoom);
    }

    @GetMapping("/{chatRoomId}")
    public ResponseEntity<GroupDetailResponse> getGroupDetails(@PathVariable Long chatRoomId) {
        Long currentUserId = getCurrentUserId();
        GroupDetailResponse response = groupService.getGroupDetails(chatRoomId, currentUserId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{chatRoomId}/members")
    public ResponseEntity<Void> addMember(@PathVariable Long chatRoomId,
                                          @Valid @RequestBody MemberActionRequest request) {
        Long currentUserId = getCurrentUserId();
        groupService.addMember(chatRoomId, request.getTargetUserId(), currentUserId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{chatRoomId}/members/{targetUserId}")
    public ResponseEntity<Void> removeMember(@PathVariable Long chatRoomId,
                                             @PathVariable Long targetUserId) {
        Long currentUserId = getCurrentUserId();
        groupService.removeMember(chatRoomId, targetUserId, currentUserId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{chatRoomId}/members/role")
    public ResponseEntity<Void> updateMemberRole(@PathVariable Long chatRoomId,
                                                 @Valid @RequestBody UpdateRoleRequest request) {
        Long currentUserId = getCurrentUserId();
        groupService.updateMemberRole(chatRoomId, request.getTargetUserId(),
                request.getNewRole(), currentUserId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{chatRoomId}/invite-code")
    public ResponseEntity<Map<String, String>> generateInviteCode(@PathVariable Long chatRoomId) {
        Long currentUserId = getCurrentUserId();
        String inviteCode = groupService.generateInviteCode(chatRoomId, currentUserId);
        return ResponseEntity.ok(Map.of("inviteCode", inviteCode));
    }

    @PostMapping("/join")
    public ResponseEntity<ChatRoom> joinByInviteCode(@RequestBody Map<String, String> request) {
        Long currentUserId = getCurrentUserId();
        String inviteCode = request.get("inviteCode");
        if (inviteCode == null || inviteCode.isBlank()) {
            throw new IllegalArgumentException("Invite code is required");
        }
        ChatRoom chatRoom = groupService.joinByInviteCode(inviteCode, currentUserId);
        return ResponseEntity.ok(chatRoom);
    }

    @GetMapping("/{chatRoomId}/members")
    public ResponseEntity<List<GroupMemberResponse>> getGroupMembers(@PathVariable Long chatRoomId) {
        List<GroupMemberResponse> members = groupService.getGroupMembers(chatRoomId);
        return ResponseEntity.ok(members);
    }
}
