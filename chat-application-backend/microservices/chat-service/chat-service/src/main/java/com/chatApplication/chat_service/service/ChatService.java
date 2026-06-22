package com.chatApplication.chat_service.service;

import com.chatApplication.chat_service.dto.ChatRequest;
import com.chatApplication.chat_service.dto.ChatResponse;
import com.chatApplication.chat_service.dto.GroupRequest;

import java.util.List;

public interface ChatService {

    // Private Chat
    ChatResponse createPrivateChat(ChatRequest chatRequest);

    // Group Chat
    ChatResponse createGroupChat(GroupRequest groupRequest);

    // Get all chats of a user
    List<ChatResponse> getAllChats(Long userId);

    // Group Operations
    ChatResponse addGroupMember(Long chatId, Long userId);

    void removeGroupMember(Long chatId, Long userId);

    ChatResponse renameGroup(Long chatId, String newName);

    void leaveGroup(Long chatId, Long userId);

    List<Long> getGroupMembers(Long chatId);

    void deleteGroup(Long chatId);
}