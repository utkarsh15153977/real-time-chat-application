package com.chatApplication.chat_service.service;

import com.chatApplication.chat_service.dto.ChatRequest;
import com.chatApplication.chat_service.dto.ChatResponse;
import com.chatApplication.chat_service.dto.GroupRequest;

import java.util.List;

public interface ChatService {

    // Private Chat
    ChatResponse createPrivateChat(ChatRequest chatRequest);

    // Group Chat
    ChatResponse createGroupChat(GroupRequest groupRequest, Long callerId);

    // Get all chats of a user
    List<ChatResponse> getAllChats(Long callerId);

    // Group Operations
    ChatResponse addGroupMember(Long chatId, Long targetUserId, Long callerId);

    void removeGroupMember(Long chatId, Long targetUserId, Long callerId);

    ChatResponse renameGroup(Long chatId, String newName, Long callerId);

    void leaveGroup(Long chatId, Long callerId);

    List<Long> getGroupMembers(Long chatId, Long callerId);

    void deleteGroup(Long chatId, Long callerId);
}