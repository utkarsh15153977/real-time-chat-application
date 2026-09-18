package com.chatApplication.chat_service.service;

import com.chatApplication.chat_service.dto.ChatRequest;
import com.chatApplication.chat_service.dto.ChatResponse;
import com.chatApplication.chat_service.dto.GroupRequest;
import com.chatApplication.chat_service.entity.Chat;
import com.chatApplication.chat_service.entity.ChatMember;
import com.chatApplication.chat_service.exception.AuthorizationException;
import com.chatApplication.chat_service.exception.ChatNotFoundException;
import com.chatApplication.chat_service.exception.DuplicateMemberException;
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
        return ChatResponse.builder()
                .chatId(chat.getId())
                .name(chat.getName())
                .group(chat.getIsGroup())
                .groupIcon(chat.getGroupIcon())
                .build();
    }

    private Chat getChatOrThrow(Long chatId) {
        return chatRepository.findById(chatId)
                .orElseThrow(() -> new ChatNotFoundException("Chat not found with id: " + chatId));
    }

    private void validateGroupChat(Chat chat) {
        if (!chat.getIsGroup()) {
            throw new GroupNotFoundException("Chat is not a group");
        }
    }

    private void validateMembership(Long chatId, Long userId) {
        if (!chatMemberRepository.existsByChatIdAndUserId(chatId, userId)) {
            throw new AuthorizationException("User is not a member of this group");
        }
    }

    private void validateAdmin(Long chatId, Long userId) {
        ChatMember member = chatMemberRepository.findByChatIdAndUserId(chatId, userId)
                .orElseThrow(() -> new AuthorizationException("User is not a member of this group"));
        if (!Boolean.TRUE.equals(member.getAdmin())) {
            throw new AuthorizationException("Only group admins can perform this action");
        }
    }

    private void validateCreator(Chat chat, Long callerId) {
        if (!callerId.equals(chat.getCreatedBy())) {
            throw new AuthorizationException("Only the group creator can perform this action");
        }
    }

    @Override
    @Transactional
    public ChatResponse createPrivateChat(ChatRequest chatRequest) {
        Long senderId = chatRequest.getSenderId();
        Long receiverId = chatRequest.getReceiverId();

        if (senderId.equals(receiverId)) {
            throw new IllegalArgumentException("Cannot create a private chat with yourself");
        }

        java.util.Optional<Chat> existingChat =
                chatRepository.findPrivateChatBetween(senderId, receiverId);

        if (existingChat.isPresent()) {
            return map(existingChat.get());
        }

        Chat chat = Chat.builder()
                .isGroup(false)
                .name(null)
                .groupIcon(null)
                .createdBy(senderId)
                .build();

        chat = chatRepository.save(chat);

        chatMemberRepository.save(
                ChatMember.builder()
                        .chatId(chat.getId())
                        .userId(senderId)
                        .admin(false)
                        .build()
        );

        chatMemberRepository.save(
                ChatMember.builder()
                        .chatId(chat.getId())
                        .userId(receiverId)
                        .admin(false)
                        .build()
        );

        return map(chat);
    }

    @Override
    @Transactional
    public ChatResponse createGroupChat(GroupRequest groupRequest, Long callerId) {
        if (groupRequest.getName() == null || groupRequest.getName().isBlank()) {
            throw new IllegalArgumentException("Group name cannot be empty");
        }

        Chat chat = Chat.builder()
                .name(groupRequest.getName())
                .isGroup(true)
                .groupIcon(groupRequest.getGroupIcon())
                .createdBy(callerId)
                .build();

        chat = chatRepository.save(chat);

        chatMemberRepository.save(
                ChatMember.builder()
                        .chatId(chat.getId())
                        .userId(callerId)
                        .admin(true)
                        .build()
        );

        if (groupRequest.getMembers() != null) {
            for (Long member : groupRequest.getMembers()) {
                if (member.equals(callerId)) {
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

    @Override
    public List<ChatResponse> getAllChats(Long callerId) {
        List<ChatMember> chatMembers = chatMemberRepository.findByUserId(callerId);
        List<ChatResponse> responses = new ArrayList<>();

        for (ChatMember member : chatMembers) {
            Chat chat = chatRepository.findById(member.getChatId())
                    .orElseThrow(() -> new ChatNotFoundException("Chat not found with id: " + member.getChatId()));
            responses.add(map(chat));
        }

        return responses;
    }

    @Override
    @Transactional
    public ChatResponse addGroupMember(Long chatId, Long targetUserId, Long callerId) {
        Chat chat = getChatOrThrow(chatId);
        validateGroupChat(chat);
        validateAdmin(chatId, callerId);

        boolean exists = chatMemberRepository.existsByChatIdAndUserId(chatId, targetUserId);

        if (exists) {
            throw new DuplicateMemberException("User " + targetUserId + " is already a member of chat " + chatId);
        }

        chatMemberRepository.save(
                ChatMember.builder()
                        .chatId(chatId)
                        .userId(targetUserId)
                        .admin(false)
                        .build()
        );

        return map(chat);
    }

    @Override
    @Transactional
    public void removeGroupMember(Long chatId, Long targetUserId, Long callerId) {
        Chat chat = getChatOrThrow(chatId);
        validateGroupChat(chat);

        boolean isSelfRemoval = callerId.equals(targetUserId);
        if (!isSelfRemoval) {
            validateAdmin(chatId, callerId);
        }

        ChatMember member = chatMemberRepository.findByChatIdAndUserId(chatId, targetUserId)
                .orElseThrow(() -> new MemberNotFoundException(
                        "Member not found in chat " + chatId));

        chatMemberRepository.delete(member);
    }

    @Override
    @Transactional
    public ChatResponse renameGroup(Long chatId, String newName, Long callerId) {
        Chat chat = getChatOrThrow(chatId);
        validateGroupChat(chat);
        validateAdmin(chatId, callerId);

        if (newName == null || newName.isBlank()) {
            throw new IllegalArgumentException("Invalid group name");
        }

        chat.setName(newName);
        chatRepository.save(chat);

        return map(chat);
    }

    @Override
    @Transactional
    public void leaveGroup(Long chatId, Long callerId) {
        Chat chat = getChatOrThrow(chatId);
        validateGroupChat(chat);
        validateMembership(chatId, callerId);

        ChatMember member = chatMemberRepository.findByChatIdAndUserId(chatId, callerId)
                .orElseThrow(() -> new MemberNotFoundException(
                        "Member not found in chat " + chatId));

        chatMemberRepository.delete(member);
    }

    @Override
    public List<Long> getGroupMembers(Long chatId, Long callerId) {
        Chat chat = getChatOrThrow(chatId);
        validateGroupChat(chat);
        validateMembership(chatId, callerId);

        List<ChatMember> members = chatMemberRepository.findByChatId(chatId);
        List<Long> result = new ArrayList<>();

        for (ChatMember member : members) {
            result.add(member.getUserId());
        }

        return result;
    }

    @Override
    @Transactional
    public void deleteGroup(Long chatId, Long callerId) {
        Chat chat = getChatOrThrow(chatId);
        validateGroupChat(chat);
        validateCreator(chat, callerId);

        List<ChatMember> members = chatMemberRepository.findByChatId(chatId);
        chatMemberRepository.deleteAll(members);
        chatRepository.delete(chat);
    }
}
