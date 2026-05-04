package com.chatbackend.chat_application_backend.service.serviceImpl;

import com.chatbackend.chat_application_backend.entity.ChatRoom;
import com.chatbackend.chat_application_backend.entity.User;
import com.chatbackend.chat_application_backend.exception.ChatNotFoundException;
import com.chatbackend.chat_application_backend.exception.UserNotFoundException;
import com.chatbackend.chat_application_backend.repository.ChatRepository;
import com.chatbackend.chat_application_backend.repository.UserRepository;
import com.chatbackend.chat_application_backend.service.ChatService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class ChatRoomServiceImpl implements ChatService {

    private final ChatRepository chatRepository;
    private final UserRepository userRepository;

    public ChatRoomServiceImpl(ChatRepository chatRepository, UserRepository userRepository) {
        this.chatRepository = chatRepository;
        this.userRepository = userRepository;
    }

    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            throw new AccessDeniedException("Unauthorized");
        }

        return userRepository.findByEmail(authentication.getName())
                .or(() -> userRepository.findByPhone(authentication.getName()))
                .orElseThrow(() -> new UserNotFoundException("Current user not found"));
    }

    private void validateMembership(ChatRoom chatRoom, User user) {
        boolean isMember = chatRoom.getParticipants()
                .stream()
                .anyMatch(u -> u.getId().equals(user.getId()));

        if (!isMember) {
            throw new AccessDeniedException("Unauthorized");
        }
    }

    @Override
    public ChatRoom createPrivateChat(Long user1Id, Long user2Id) {
        User currentUser = getCurrentUser();

        if (!currentUser.getId().equals(user1Id) && !currentUser.getId().equals(user2Id)) {
            throw new AccessDeniedException("Cannot create chat for other users");
        }

        User user1 = userRepository.findById(user1Id)
                .orElseThrow(() -> new UserNotFoundException("User1 not found"));

        User user2 = userRepository.findById(user2Id)
                .orElseThrow(() -> new UserNotFoundException("User2 not found"));

        List<ChatRoom> existingChats = chatRepository.findByParticipantsContaining(user1);
        for (ChatRoom existingChat : existingChats) {
            if (!existingChat.isGroupChat()
                    && existingChat.getParticipants().size() == 2
                    && existingChat.getParticipants().stream().anyMatch(user -> user.getId().equals(user2Id))) {
                return existingChat;
            }
        }

        ChatRoom chatRoom = new ChatRoom();
        chatRoom.setGroupChat(false);
        chatRoom.setParticipants(new ArrayList<>(List.of(user1, user2)));

        return chatRepository.save(chatRoom);
    }

    @Override
    public ChatRoom createGroupChat(String groupName, List<Long> participantIds) {
        User currentUser = getCurrentUser();
        List<User> participants = new ArrayList<>();

        for (Long id : participantIds) {
            User user = userRepository.findById(id)
                    .orElseThrow(() -> new UserNotFoundException("User not found"));
            participants.add(user);
        }

        if (participants.stream().noneMatch(u -> u.getId().equals(currentUser.getId()))) {
            participants.add(currentUser);
        }

        ChatRoom chatRoom = new ChatRoom();
        chatRoom.setGroupChat(true);
        chatRoom.setGroupName(groupName);
        chatRoom.setParticipants(participants);

        return chatRepository.save(chatRoom);
    }

    @Override
    public ChatRoom getChatRoom(Long chatRoomId) {
        User currentUser = getCurrentUser();
        ChatRoom chatRoom = chatRepository.findById(chatRoomId)
                .orElseThrow(() -> new ChatNotFoundException("Chat not found"));

        validateMembership(chatRoom, currentUser);
        return chatRoom;
    }

    @Override
    public List<ChatRoom> getUserChats(Long userId) {
        User currentUser = getCurrentUser();

        if (!currentUser.getId().equals(userId)) {
            throw new AccessDeniedException("Cannot view others chats");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        return chatRepository.findByParticipantsContaining(user);
    }

    @Override
    public void addParticipant(Long chatRoomId, Long userId) {
        User currentUser = getCurrentUser();
        ChatRoom chatRoom = chatRepository.findById(chatRoomId)
                .orElseThrow(() -> new ChatNotFoundException("Chat not found"));

        validateMembership(chatRoom, currentUser);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        boolean exists = chatRoom.getParticipants()
                .stream()
                .anyMatch(u -> u.getId().equals(userId));

        if (exists) {
            throw new IllegalArgumentException("User already exists in chat");
        }

        chatRoom.getParticipants().add(user);
        chatRepository.save(chatRoom);
    }

    @Override
    public void removeParticipant(Long chatRoomId, Long userId) {
        User currentUser = getCurrentUser();
        ChatRoom chatRoom = chatRepository.findById(chatRoomId)
                .orElseThrow(() -> new ChatNotFoundException("Chat not found"));

        validateMembership(chatRoom, currentUser);
        chatRoom.getParticipants().removeIf(u -> u.getId().equals(userId));
        chatRepository.save(chatRoom);
    }

    @Override
    public void deleteChatRoom(Long chatRoomId) {
        User currentUser = getCurrentUser();
        ChatRoom chatRoom = chatRepository.findById(chatRoomId)
                .orElseThrow(() -> new ChatNotFoundException("Chat not found"));

        validateMembership(chatRoom, currentUser);
        chatRepository.delete(chatRoom);
    }
}
