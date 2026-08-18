package com.chatapplication.group_chat.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateGroupRequest {
    /**
     * User creating the group
     */
    @NotNull(message = "Creator Id is required")
    private Long creatorId;

    /**
     * Group Name
     */
    @NotBlank(message = "Group name is required")
    @Size(min = 3, max = 100, message = "Group name must be between 3 and 100 characters")
    private String groupName;

    /**
     * Group Description
     */
    @Size(max = 500, message = "Description cannot exceed 500 characters")
    private String description;

    /**
     * Group Profile Picture
     */
    private MultipartFile groupImage;

    /**
     * Initial Members
     */
    @NotEmpty(message = "At least one member must be added")
    @Size(min = 2, message = "At least two members are required")
    private List<Long> memberIds;

    /**
     * Allow only admins to send messages
     */
    @Builder.Default
    private Boolean adminOnlyMessages = false;

    /**
     * Allow members to edit group information
     */
    @Builder.Default
    private Boolean membersCanEditInfo = true;

    /**
     * Allow members to invite others
     */
    @Builder.Default
    private Boolean membersCanInvite = true;
}
