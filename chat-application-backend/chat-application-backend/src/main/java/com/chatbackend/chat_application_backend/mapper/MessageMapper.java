package com.chatbackend.chat_application_backend.mapper;

import com.chatbackend.chat_application_backend.dto.MessageResponse;
import com.chatbackend.chat_application_backend.entity.Message;

import java.util.List;

public class MessageMapper {

    public static MessageResponse toMessageResponseDTO(Message message) {
        if (message == null) return null;

        return MessageResponse.builder()
                .id(message.getId())
                .senderId(message.getSender().getId())
                .chatRoomId(message.getChatRoom().getId())
                .content(message.getContent())
                .timestamp(message.getTimestamp())
                .status(message.getStatus())
                .build();
    }

    public static List<MessageResponse> toMessageResponseDTO(List<Message> messages) {
        return messages.stream()
                .map(MessageMapper::toMessageResponseDTO)
                .toList();
    }
}