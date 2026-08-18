package com.chatapplication.group_chat.mapper;

import com.chatapplication.group_chat.dto.response.ConversationResponse;
import com.chatapplication.group_chat.entitty.Conversation;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface ConversationMapper {
    /**
     * Entity -> Response DTO
     */
    @Mapping(target = "conversationId", source = "id")
    @Mapping(target = "lastMessageId", source = "lastMessage.id")
    @Mapping(target = "lastMessage", source = "lastMessage.content")
    @Mapping(target = "lastMessageTime", source = "lastMessage.createdAt")
    @Mapping(target = "lastMessageStatus", source = "lastMessage.status")
    ConversationResponse toResponse(Conversation conversation);

    /**
     * Entity List -> Response DTO List
     */
    List<ConversationResponse> toResponseList(List<Conversation> conversations);
}
