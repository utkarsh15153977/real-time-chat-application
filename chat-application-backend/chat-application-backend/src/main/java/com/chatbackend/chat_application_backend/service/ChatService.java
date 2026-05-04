package com.chatbackend.chat_application_backend.service;

import com.chatbackend.chat_application_backend.entity.ChatRoom;

import java.util.List;

public interface ChatService {
    // Create private chat between two users
    ChatRoom createPrivateChat(Long user1Id, Long user2Id);

    // Create group chat
    ChatRoom createGroupChat(String groupName, List<Long> participantIds);

    // Get chat room by id
    ChatRoom getChatRoom(Long chatRoomId);

    // Get all chats for a user
    List<ChatRoom> getUserChats(Long userId);

    // Add participant to group
    void addParticipant(Long chatRoomId, Long userId);

    // Remove participant from group
    void removeParticipant(Long chatRoomId, Long userId);

    // Delete chat
    void deleteChatRoom(Long chatRoomId);
}
