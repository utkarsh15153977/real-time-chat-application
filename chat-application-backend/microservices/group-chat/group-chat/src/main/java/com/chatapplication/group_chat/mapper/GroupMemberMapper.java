package com.chatapplication.group_chat.mapper;

import com.chatapplication.group_chat.dto.response.GroupMemberResponse;
import com.chatapplication.group_chat.entitty.GroupMember;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface GroupMemberMapper {
    GroupMemberResponse toResponse(GroupMember member);
    List<GroupMemberResponse> toResponseList(List<GroupMember> members);
}
