package com.chatbackend.chat_application_backend.dto;

import com.chatbackend.chat_application_backend.entity.GroupRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

public class GroupDTOs {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateGroupRequest {
        @NotBlank(message = "Group name is required")
        private String name;
        private String groupIconUrl;
        private List<Long> initialMemberIds;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MemberActionRequest {
        @NotNull(message = "Target user ID is required")
        private Long targetUserId;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateRoleRequest {
        @NotNull(message = "Target user ID is required")
        private Long targetUserId;
        @NotNull(message = "New role is required")
        private GroupRole newRole;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GroupMemberResponse {
        private Long userId;
        private String username;
        private String email;
        private GroupRole role;
        private LocalDateTime joinedAt;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GroupDetailResponse {
        private Long chatRoomId;
        private String name;
        private String groupIconUrl;
        private String inviteCode;
        private List<GroupMemberResponse> members;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GroupEventNotification {
        private String eventType;
        private Long chatRoomId;
        private Long actorId;
        private Long targetUserId;
    }
}
