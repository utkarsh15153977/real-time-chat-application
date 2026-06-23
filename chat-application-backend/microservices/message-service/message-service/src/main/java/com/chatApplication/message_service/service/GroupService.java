package com.chatApplication.message_service.service;

import com.chatApplication.message_service.dto.GroupMessageRequest;
import com.chatApplication.message_service.entity.Group;
import com.chatApplication.message_service.entity.GroupMessage;

import java.util.List;

public interface GroupService {
    Group createGroup(
            String groupName,
            String createdBy);

    void addMember(
            Long groupId,
            String userId);

    List<GroupMessage> getMessages(
            Long groupId);

    GroupMessage sendMessage(
            GroupMessageRequest request);
}
