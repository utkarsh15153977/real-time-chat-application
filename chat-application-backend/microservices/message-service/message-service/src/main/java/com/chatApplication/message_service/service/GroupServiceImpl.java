package com.chatApplication.message_service.service;

import com.chatApplication.message_service.dto.GroupMessageRequest;
import com.chatApplication.message_service.entity.Group;
import com.chatApplication.message_service.entity.GroupMember;
import com.chatApplication.message_service.entity.GroupMessage;
import com.chatApplication.message_service.repository.GroupMemberRepository;
import com.chatApplication.message_service.repository.GroupMessageRepository;
import com.chatApplication.message_service.repository.GroupRepository;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class GroupServiceImpl implements GroupService {
    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final GroupMessageRepository groupMessageRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public GroupServiceImpl(GroupRepository groupRepository,
                            GroupMemberRepository groupMemberRepository,
                            GroupMessageRepository groupMessageRepository,
                            SimpMessagingTemplate messagingTemplate) {
        this.groupRepository = groupRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.groupMessageRepository = groupMessageRepository;
        this.messagingTemplate = messagingTemplate;
    }

    @Override
    public Group createGroup(
            String groupName,
            String createdBy) {

        Group group =
                Group.builder()
                        .groupName(groupName)
                        .createdBy(createdBy)
                        .build();

        Group saved =
                groupRepository.save(group);

        groupMemberRepository.save(
                GroupMember.builder()
                        .groupId(saved.getGroupId())
                        .userId(createdBy)
                        .admin(true)
                        .build());

        return saved;
    }

    @Override
    public void addMember(
            Long groupId,
            String userId) {

        groupMemberRepository.save(
                GroupMember.builder()
                        .groupId(groupId)
                        .userId(userId)
                        .build());
    }

    @Override
    public GroupMessage sendMessage(
            GroupMessageRequest request) {

        GroupMessage message =
                GroupMessage.builder()
                        .groupId(request.getGroupId())
                        .senderId(request.getSenderId())
                        .content(request.getContent())
                        .build();

        GroupMessage saved =
                groupMessageRepository.save(message);

        messagingTemplate.convertAndSend(
                "/topic/group/" +
                        request.getGroupId(),
                saved);

        return saved;
    }

    @Override
    public List<GroupMessage> getMessages(
            Long groupId) {

        return groupMessageRepository
                .findByGroupIdOrderBySentAtAsc(
                        groupId);
    }
}
