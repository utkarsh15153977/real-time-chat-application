package com.chatapplication.group_chat.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AddMemberRequest {
    /**
     * Group ID
     */
    @NotNull(message = "Group Id is required")
    private Long groupId;

    /**
     * User performing the action
     * (Must be Admin or Owner)
     */
    @NotNull(message = "Requester Id is required")
    private Long requesterId;

    /**
     * Members to be added
     */
    @NotEmpty(message = "At least one member must be selected")
    private List<Long> memberIds;
}
