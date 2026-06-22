package com.chatApplication.chat_service.service;

import com.chatApplication.chat_service.dto.ChatRequest;
import com.chatApplication.chat_service.dto.ChatResponse;
import com.chatApplication.chat_service.dto.GroupRequest;
import com.chatApplication.chat_service.entity.Chat;
import com.chatApplication.chat_service.entity.ChatMember;
import com.chatApplication.chat_service.exception.ChatNotFoundException;
import com.chatApplication.chat_service.exception.GroupNotFoundException;
import com.chatApplication.chat_service.exception.MemberNotFoundException;
import com.chatApplication.chat_service.repository.ChatMemberRepository;
import com.chatApplication.chat_service.repository.ChatRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class ChatServiceImpl implements ChatService {

    private final ChatRepository chatRepository;
    private final ChatMemberRepository chatMemberRepository;

    public ChatServiceImpl(
            ChatRepository chatRepository,
            ChatMemberRepository chatMemberRepository
    ) {
        this.chatRepository = chatRepository;
        this.chatMemberRepository = chatMemberRepository;
    }

    private ChatResponse map(Chat chat) {
        return new ChatResponse(
                chat.getId(),
                chat.getName(),
                chat.getIsGroup(),
                chat.getGroupIcon()
        );
    }

    // Create Private Chat
    @Override
    @Transactional
    public ChatResponse createPrivateChat(ChatRequest chatRequest) {

        Chat chat = Chat.builder()
                .isGroup(false)
                .name(null)
                .groupIcon(null)
                .createdBy(chatRequest.getSenderId())
                .build();

        chat = chatRepository.save(chat);

        chatMemberRepository.save(
                ChatMember.builder()
                        .chatId(chat.getId())
                        .userId(chatRequest.getSenderId())
                        .admin(false)
                        .build()
        );

        chatMemberRepository.save(
                ChatMember.builder()
                        .chatId(chat.getId())
                        .userId(chatRequest.getReceiverId())
                        .admin(false)
                        .build()
        );

        return map(chat);
    }

    // Create Group Chat
    @Override
    @Transactional
    public ChatResponse createGroupChat(GroupRequest groupRequest) {

        if (groupRequest.getName() == null ||
                groupRequest.getName().isBlank()) {
            throw new IllegalArgumentException(
                    "Group name cannot be empty"
            );
        }

        Chat chat = Chat.builder()
                .name(groupRequest.getName())
                .isGroup(true)
                .groupIcon(groupRequest.getGroupIcon())
                .createdBy(groupRequest.getAdminId())
                .build();

        chat = chatRepository.save(chat);

        // Admin
        chatMemberRepository.save(
                ChatMember.builder()
                        .chatId(chat.getId())
                        .userId(groupRequest.getAdminId())
                        .admin(true)
                        .build()
        );

        // Members
        if (groupRequest.getMembers() != null) {
            for (Long member : groupRequest.getMembers()) {

                if (member.equals(groupRequest.getAdminId())) {
                    continue;
                }

                chatMemberRepository.save(
                        ChatMember.builder()
                                .chatId(chat.getId())
                                .userId(member)
                                .admin(false)
                                .build()
                );
            }
        }

        return map(chat);
    }

    // Get All Chats of User
    @Override
    public List<ChatResponse> getAllChats(Long userId) {

        List<ChatMember> chatMembers =
                chatMemberRepository.findByUserId(userId);

        List<ChatResponse> responses =
                new ArrayList<>();

        for (ChatMember member : chatMembers) {

            Chat chat =
                    chatRepository.findById(member.getChatId())
                            .orElseThrow(
                                    () -> new ChatNotFoundException(
                                            "Chat not found"
                                    )
                            );

            responses.add(map(chat));
        }

        return responses;
    }

    // Add Member
    @Override
    @Transactional
    public ChatResponse addGroupMember(
            Long chatId,
            Long userId
    ) {

        Chat chat =
                chatRepository.findById(chatId)
                        .orElseThrow(
                                () -> new ChatNotFoundException(
                                        "Chat not found"
                                )
                        );

        if (!chat.getIsGroup()) {
            throw new GroupNotFoundException(
                    "Private chat"
            );
        }

        boolean exists =
                chatMemberRepository
                        .existsByChatIdAndUserId(
                                chatId,
                                userId
                        );

        if (!exists) {

            chatMemberRepository.save(
                    ChatMember.builder()
                            .chatId(chatId)
                            .userId(userId)
                            .admin(false)
                            .build()
            );
        }

        return map(chat);
    }

    // Remove Member
    @Override
    @Transactional
    public void removeGroupMember(
            Long chatId,
            Long userId
    ) {

        List<ChatMember> members =
                chatMemberRepository.findByChatId(chatId);

        for (ChatMember member : members) {

            if (member.getUserId().equals(userId)) {
                chatMemberRepository.delete(member);
                return;
            }
        }

        throw new MemberNotFoundException(
                "Member not found"
        );
    }

    // Rename Group
    @Override
    @Transactional
    public ChatResponse renameGroup(
            Long chatId,
            String newName
    ) {

        Chat chat =
                chatRepository.findById(chatId)
                        .orElseThrow(
                                () -> new ChatNotFoundException(
                                        "Chat not found"
                                )
                        );

        if (!chat.getIsGroup()) {
            throw new GroupNotFoundException(
                    "Private chat"
            );
        }

        if (newName == null ||
                newName.isBlank()) {
            throw new IllegalArgumentException(
                    "Invalid group name"
            );
        }

        chat.setName(newName);

        chatRepository.save(chat);

        return map(chat);
    }

    // Leave Group
    @Override
    @Transactional
    public void leaveGroup(
            Long chatId,
            Long userId
    ) {

        List<ChatMember> members =
                chatMemberRepository.findByChatId(chatId);

        for (ChatMember member : members) {

            if (member.getUserId().equals(userId)) {
                chatMemberRepository.delete(member);
                return;
            }
        }

        throw new MemberNotFoundException(
                "Member not found"
        );
    }

    // Get Group Members
    @Override
    public List<Long> getGroupMembers(
            Long chatId
    ) {

        Chat chat =
                chatRepository.findById(chatId)
                        .orElseThrow(
                                () -> new ChatNotFoundException(
                                        "Chat not found"
                                )
                        );

        if (!chat.getIsGroup()) {
            throw new GroupNotFoundException(
                    "Private chat"
            );
        }

        List<ChatMember> members =
                chatMemberRepository.findByChatId(chatId);

        List<Long> result =
                new ArrayList<>();

        for (ChatMember member : members) {
            result.add(member.getUserId());
        }

        return result;
    }

    // Delete Group
    @Override
    @Transactional
    public void deleteGroup(
            Long chatId
    ) {

        Chat chat =
                chatRepository.findById(chatId)
                        .orElseThrow(
                                () -> new ChatNotFoundException(
                                        "Chat not found"
                                )
                        );

        if (!chat.getIsGroup()) {
            throw new GroupNotFoundException(
                    "Private chat"
            );
        }

        List<ChatMember> members =
                chatMemberRepository.findByChatId(chatId);

        chatMemberRepository.deleteAll(members);

        chatRepository.delete(chat);
    }
}