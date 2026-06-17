package com.chatApplication.chat_service.service;

import com.chatApplication.chat_service.dto.ChatRequest;
import com.chatApplication.chat_service.dto.ChatResponse;
import com.chatApplication.chat_service.dto.GroupRequest;

import java.util.List;

public interface ChatService {
    ChatResponse createPrivateChat(ChatRequest chatRequest);
    ChatResponse createGroupChat(GroupRequest groupRequest);
    List<ChatResponse> getAllChats(long userId);
    ChatResponse addGroupMember(Long chatId, Long userId);
    void removeGroupMember(Long chatId, Long userId);
}
