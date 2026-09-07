package com.chatbackend.chat_application_backend.service;

public interface PresenceService {
    void setUserOnline(Long userId);
    void setUserOffline(Long userId);
    void heartbeat(Long userId);
    void setTyping(Long chatRoomId, Long userId, boolean isTyping);
    boolean isUserOnline(Long userId);
}
