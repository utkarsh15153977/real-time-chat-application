package com.chatapplication.group_chat.mapper;

import com.chatapplication.group_chat.dto.response.PresenceResponse;
import com.chatapplication.group_chat.entitty.UserPresence;
import org.mapstruct.*;

import java.util.List;

@Mapper(componentModel = "spring")
public interface UserPresenceMapper {
    /**
     * Entity -> Response DTO
     */
    @Mapping(target = "userName", ignore = true)
    @Mapping(target = "profilePicture", ignore = true)
    @Mapping(target = "typing", ignore = true)
    @Mapping(target = "lastActivity", source = "updatedAt")
    PresenceResponse toResponse(UserPresence userPresence);

    /**
     * Entity List -> Response DTO List
     */
    List<PresenceResponse> toResponseList(List<UserPresence> userPresences);

    /**
     * Update existing entity
     */
    @BeanMapping(
            nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE
    )
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "ipAddress", ignore = true)
    void updateEntity(
            PresenceResponse request,
            @MappingTarget UserPresence userPresence
    );
}
