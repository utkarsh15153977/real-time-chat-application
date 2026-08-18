package com.chatapplication.group_chat.mapper;

import com.chatapplication.group_chat.dto.request.ReactionRequest;
import com.chatapplication.group_chat.dto.response.ReactionResponse;
import com.chatapplication.group_chat.entitty.MessageReaction;
import org.mapstruct.*;

import java.util.List;

@Mapper(componentModel = "spring")
public interface MessageReactionMapper {
    @Mapping(target = "reactionId", source = "id")
    @Mapping(target = "messageId", source = "chatMessage.id")
    ReactionResponse toResponse(MessageReaction reaction);

    List<ReactionResponse> toResponseList(List<MessageReaction> reactions);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "chatMessage", ignore = true)
    @Mapping(target = "groupMessage", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    MessageReaction toEntity(ReactionRequest request);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "chatMessage", ignore = true)
    @Mapping(target = "groupMessage", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntity(
            ReactionRequest request,
            @MappingTarget MessageReaction reaction
    );
}
