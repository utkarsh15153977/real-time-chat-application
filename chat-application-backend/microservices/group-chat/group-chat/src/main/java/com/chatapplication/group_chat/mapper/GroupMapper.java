package com.chatapplication.group_chat.mapper;

import com.chatapplication.group_chat.dto.request.CreateGroupRequest;
import com.chatapplication.group_chat.dto.response.GroupResponse;
import com.chatapplication.group_chat.entitty.Group;
import org.mapstruct.*;

import java.util.List;

@Mapper(
        componentModel = "spring",
        uses = {
                GroupMemberMapper.class
        }
)
public interface GroupMapper {
    /**
     * Entity -> Response DTO
     */
    @Mapping(target = "groupId", source = "id")
    @Mapping(target = "lastMessage", ignore = true)
    @Mapping(target = "groupName", source = "name")
    @Mapping(target = "memberCount",
            expression = "java(group.getMembers() != null ? group.getMembers().size() : 0)")
    GroupResponse toResponse(Group group);

    /**
     * Entity List -> DTO List
     */
    List<GroupResponse> toResponseList(List<Group> groups);

    /**
     * Create Request -> Entity
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "members", ignore = true)
    @Mapping(target = "messages", ignore = true)
    @Mapping(target = "lastMessage", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "active", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "memberCount", ignore = true)
    @Mapping(target = "groupImage", ignore = true)
    Group toEntity(CreateGroupRequest request);

    /**
     * Update Existing Entity
     */
    @BeanMapping(
            nullValuePropertyMappingStrategy =
                    NullValuePropertyMappingStrategy.IGNORE
    )
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "members", ignore = true)
    @Mapping(target = "messages", ignore = true)
    @Mapping(target = "lastMessage", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "active", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "memberCount", ignore = true)
    @Mapping(target = "groupImage", ignore = true)
    void updateEntity(
            CreateGroupRequest request,
            @MappingTarget Group entity
    );
}
