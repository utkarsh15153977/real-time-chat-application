package com.chatapplication.group_chat.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RemoveMemberRequest {
    /**
     * Group ID
     */
    @NotNull(message = "Group Id is required")
    private Long groupId;

    /**
     * User performing the action
     */
    @NotNull(message = "Requester Id is required")
    private Long requesterId;

    /**
     * Members to remove
     */
    @NotEmpty(message = "At least one member must be selected")
    private List<Long> memberIds;

    /**
     * Optional reason for removal
     */
    @Size(max = 255, message = "Reason cannot exceed 255 characters")
    private String reason;

    /**
     * True when user is leaving the group voluntarily
     */
    @Builder.Default
    private Boolean leaveGroup = false;
}
