package com.chatapplication.group_chat.mapper;

import com.chatapplication.group_chat.dto.request.AttachmentRequest;
import com.chatapplication.group_chat.dto.response.AttachmentResponse;
import com.chatapplication.group_chat.entitty.Attachment;
import org.mapstruct.*;

import java.util.List;

@Mapper(componentModel = "spring")
public interface AttachmentMapper {
    /**
     * Entity -> Response DTO
     */
    @Mapping(target = "attachmentId", source = "id")
    AttachmentResponse toResponse(Attachment attachment);

    /**
     * Entity List -> Response DTO List
     */
    List<AttachmentResponse> toResponseList(List<Attachment> attachments);

    /**
     * Request DTO -> Entity
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "message", ignore = true)
    @Mapping(target = "uploadedAt", ignore = true)
    Attachment toEntity(AttachmentRequest request);

    /**
     * Update existing entity
     */
    @BeanMapping(
            nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE
    )
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "message", ignore = true)
    @Mapping(target = "uploadedAt", ignore = true)
    void updateEntity(
            AttachmentRequest request,
            @MappingTarget Attachment attachment
    );
}
