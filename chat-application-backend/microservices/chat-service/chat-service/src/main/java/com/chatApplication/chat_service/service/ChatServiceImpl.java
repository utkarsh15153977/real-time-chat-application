package com.chatApplication.chat_service.service;

import com.chatApplication.chat_service.dto.ChatRequest;
import com.chatApplication.chat_service.dto.ChatResponse;
import com.chatApplication.chat_service.dto.GroupRequest;
import com.chatApplication.chat_service.entity.Chat;
import com.chatApplication.chat_service.entity.ChatMember;
import com.chatApplication.chat_service.repository.ChatMemberRepository;
import com.chatApplication.chat_service.repository.ChatRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class ChatServiceImpl implements ChatService {
    private ChatRepository chatRepository;
    private ChatMemberRepository chatMemberRepository;

    public ChatServiceImpl(ChatRepository chatRepository, ChatMemberRepository chatMemberRepository) {
        this.chatRepository = chatRepository;
        this.chatMemberRepository = chatMemberRepository;
    }

    @Override
    public ChatResponse createPrivateChat(ChatRequest chatRequest) {
        Chat chat = Chat.builder()
                .isGroup(false)
                .name(null)
                .groupIcon(null)
                .createdBy(chatRequest.getSenderId())
                .build();

        chatRepository.save(chat);

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

    @Override
    public ChatResponse createGroupChat(GroupRequest groupRequest){
        Chat chat = Chat.builder()
                .name(groupRequest.getName())
                .isGroup(true)
                .groupIcon(groupRequest.getGroupIcon())
                .createdBy(groupRequest.getAdminId())
                .build();

        chatRepository.save(chat);

        chatMemberRepository.save(
                ChatMember.builder()
                        .chatId(chat.getId())
                        .userId(groupRequest.getAdminId())
                        .admin(true)
                        .build()
        );

        for(Long member :  groupRequest.getMembers()){
            chatMemberRepository.save(
                    ChatMember.builder()
                            .chatId(chat.getId())
                            .userId(member)
                            .admin(false)
                            .build()
            );
        }

        return map(chat);
    }

    @Override
    public List<ChatResponse> getAllChats(long userId){
        List<ChatMember> chatMembers = chatMemberRepository.findByChatId(userId);
        List<ChatResponse> chatResponses = new ArrayList<>();

        for(ChatMember chatMember : chatMembers){
            Chat chat = chatRepository.findById(chatMember.getChatId()).orElseThrow(
                    () -> new RuntimeException("Chat not found")
            );
            chatResponses.add(
                    map(chat)
            );
        }
        return chatResponses;
    }

    @Override
    public ChatResponse addGroupMember(Long chatId, Long userId){
        chatMemberRepository.save(
                ChatMember.builder()
                        .chatId(chatId)
                        .userId(userId)
                        .admin(false)
                        .build()
        );
        return null;
    }

    @Override
    public void removeGroupMember(Long chatId, Long userId){
        List<ChatMember> chatMembers =  chatMemberRepository.findByChatId(chatId);

        for(ChatMember chatMember : chatMembers){
            if(chatMember.getId().equals(userId)){
                chatMemberRepository.delete(chatMember);
                break;
            }
        }
        throw new RuntimeException("Members not found");
    }

    private ChatResponse map(
            Chat chat) {

        return new ChatResponse(
                chat.getId(),
                chat.getName(),
                chat.getIsGroup(),
                chat.getGroupIcon()
        );
    }
}