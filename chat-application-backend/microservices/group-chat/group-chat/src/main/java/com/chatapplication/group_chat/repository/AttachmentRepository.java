package com.chatapplication.group_chat.repository;

import com.chatapplication.group_chat.entitty.Attachment;
import com.chatapplication.group_chat.entitty.AttachmentType;
import com.chatapplication.group_chat.entitty.ChatMessage;
import com.chatapplication.group_chat.entitty.GroupMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AttachmentRepository extends JpaRepository<Attachment, Long> {
    /**
     * Find attachment by private chat message
     */
    Optional<Attachment> findByChatMessage(
            ChatMessage chatMessage
    );

    /**
     * Find attachment by group message
     */
    Optional<Attachment> findByGroupMessage(
            GroupMessage groupMessage
    );

    /**
     * Find all attachments of a specific type
     */
    List<Attachment> findByAttachmentType(
            AttachmentType attachmentType
    );

    /**
     * Find uploaded attachments
     */
    List<Attachment> findByUploadedTrue();

    /**
     * Find attachments by content type
     */
    List<Attachment> findByContentType(
            String contentType
    );

    /**
     * Find attachment by stored filename
     */
    Optional<Attachment> findByStoredFileName(
            String storedFileName
    );

    /**
     * Find attachment by original filename
     */
    List<Attachment> findByFileName(
            String fileName
    );

    /**
     * Check stored filename exists
     */
    boolean existsByStoredFileName(
            String storedFileName
    );

    /**
     * Count by attachment type
     */
    long countByAttachmentType(
            AttachmentType attachmentType
    );

    /**
     * Count uploaded attachments
     */
    long countByUploadedTrue();

    /**
     * Delete attachment of private message
     */
    void deleteByChatMessage(
            ChatMessage chatMessage
    );

    /**
     * Delete attachment of group message
     */
    void deleteByGroupMessage(
            GroupMessage groupMessage
    );

    /**
     * Find image attachments
     */
    List<Attachment> findByAttachmentTypeOrderByCreatedAtDesc(
            AttachmentType attachmentType
    );
}
