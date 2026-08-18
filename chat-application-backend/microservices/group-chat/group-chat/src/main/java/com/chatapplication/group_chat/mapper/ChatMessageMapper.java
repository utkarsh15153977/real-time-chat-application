package com.chatapplication.group_chat.mapper;

import com.chatapplication.group_chat.dto.request.ChatMessageRequest;
import com.chatapplication.group_chat.dto.request.EditMessageRequest;
import com.chatapplication.group_chat.dto.response.ChatMessageResponse;
import com.chatapplication.group_chat.entitty.ChatMessage;
import org.mapstruct.*;

import java.util.List;

@Mapper(
        componentModel = "spring",
        uses = {
                AttachmentMapper.class,
                MessageReactionMapper.class
        }
)
public interface ChatMessageMapper {
        /**
         * Entity -> Response DTO
         */
        @Mapping(target = "messageId", source = "id")
        @Mapping(target = "attachment", source = "attachment")
        @Mapping(target = "reactions", source = "reactions")
        @Mapping(target = "replyToMessageId", source = "replyTo.id")
        @Mapping(target = "replyMessage", source = "replyTo.content")
        ChatMessageResponse toResponse(ChatMessage message);

    /**
     * Entity List -> DTO List
     */
    List<ChatMessageResponse> toResponseList(List<ChatMessage> messages);

    /**
         * Request DTO -> Entity
         */
        @Mapping(target = "id", ignore = true)
        @Mapping(target = "conversation", ignore = true)
        @Mapping(target = "attachment", ignore = true)
        @Mapping(target = "replyTo", ignore = true)
        @Mapping(target = "reactions", ignore = true)
        @Mapping(target = "status", ignore = true)
        @Mapping(target = "createdAt", ignore = true)
        @Mapping(target = "editedAt", ignore = true)
        @Mapping(target = "deletedAt", ignore = true)
        @Mapping(target = "deliveredAt", ignore = true)
        @Mapping(target = "seenAt", ignore = true)
        @Mapping(target = "edited", ignore = true)
        @Mapping(target = "deleted", ignore = true)
        ChatMessage toEntity(ChatMessageRequest request);

        /**
         * Update existing entity while editing message
         */
        @BeanMapping(
                nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE
        )
        @Mapping(target = "id", ignore = true)
        @Mapping(target = "senderId", ignore = true)
        @Mapping(target = "receiverId", ignore = true)
        @Mapping(target = "conversation", ignore = true)
        @Mapping(target = "status", ignore = true)
        @Mapping(target = "createdAt", ignore = true)
        @Mapping(target = "editedAt", ignore = true)
        @Mapping(target = "deletedAt", ignore = true)
        @Mapping(target = "attachment", ignore = true)
        @Mapping(target = "replyTo", ignore = true)
        @Mapping(target = "reactions", ignore = true)
        @Mapping(target = "id", ignore = true)
        void updateEntity(
                EditMessageRequest request,
                @MappingTarget ChatMessage entity
        );
}
