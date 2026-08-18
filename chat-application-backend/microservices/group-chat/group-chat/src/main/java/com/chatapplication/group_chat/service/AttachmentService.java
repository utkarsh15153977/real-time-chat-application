package com.chatapplication.group_chat.service;

import com.chatapplication.group_chat.dto.request.AttachmentRequest;
import com.chatapplication.group_chat.dto.response.AttachmentResponse;
import com.chatapplication.group_chat.entitty.AttachmentType;

import java.util.List;

public interface AttachmentService {

    AttachmentResponse uploadAttachment(AttachmentRequest request);
    AttachmentResponse getAttachment(Long attachmentId);
    AttachmentResponse getChatMessageAttachment(Long messageId);
    AttachmentResponse getGroupMessageAttachment(Long groupMessageId);

    /**
     * Get all uploaded attachments.
     */
    List<AttachmentResponse> getAllAttachments();

    /**
     * Get all attachments of a particular type.
     */
    List<AttachmentResponse> getAttachmentsByType(
            AttachmentType attachmentType
    );

    /**
     * Get all uploaded images.
     */
    List<AttachmentResponse> getImages();

    /**
     * Get all uploaded videos.
     */
    List<AttachmentResponse> getVideos();

    /**
     * Get all uploaded audio files.
     */
    List<AttachmentResponse> getAudios();

    /**
     * Get all uploaded documents.
     */
    List<AttachmentResponse> getDocuments();

    /**
     * Delete attachment by id.
     */
    void deleteAttachment(Long attachmentId);

    /**
     * Delete attachment of a private chat message.
     */
    void deleteChatMessageAttachment(Long messageId);

    /**
     * Delete attachment of a group chat message.
     */
    void deleteGroupMessageAttachment(Long groupMessageId);

    /**
     * Check whether attachment exists.
     */
    boolean exists(Long attachmentId);

    /**
     * Count all uploaded attachments.
     */
    long countUploadedAttachments();

    /**
     * Count attachments by type.
     */
    long countByType(
            AttachmentType attachmentType
    );
}
