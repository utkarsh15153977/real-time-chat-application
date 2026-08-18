package com.chatapplication.group_chat.service.impl;

import com.chatapplication.group_chat.dto.request.AttachmentRequest;
import com.chatapplication.group_chat.dto.response.AttachmentResponse;
import com.chatapplication.group_chat.entitty.Attachment;
import com.chatapplication.group_chat.entitty.AttachmentType;
import com.chatapplication.group_chat.entitty.ChatMessage;
import com.chatapplication.group_chat.entitty.GroupMessage;
import com.chatapplication.group_chat.exception.AttachmentNotFoundException;
import com.chatapplication.group_chat.exception.InvalidAttachmentException;
import com.chatapplication.group_chat.exception.MessageNotFoundException;
import com.chatapplication.group_chat.mapper.AttachmentMapper;
import com.chatapplication.group_chat.repository.AttachmentRepository;
import com.chatapplication.group_chat.repository.ChatMessageRepository;
import com.chatapplication.group_chat.repository.GroupMessageRepository;
import com.chatapplication.group_chat.service.AttachmentService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.stream.Collectors;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class AttachmentServiceImpl implements AttachmentService {
    private final AttachmentRepository attachmentRepository;
    private final AttachmentMapper attachmentMapper;

    @Value("${file.upload-dir}")
    private String uploadDirectory;

    private Attachment getAttachmentOrThrow(Long attachmentId) {

        return attachmentRepository.findById(attachmentId)
                .orElseThrow(() ->
                        new AttachmentNotFoundException(
                                "Attachment not found : " + attachmentId
                        ));
    }

    private void validateAttachment(MultipartFile file) {

        if (file == null || file.isEmpty()) {
            throw new InvalidAttachmentException(
                    "Attachment file is required."
            );
        }

        if (file.getSize() > 100 * 1024 * 1024) {
            throw new InvalidAttachmentException(
                    "Maximum attachment size is 100 MB."
            );
        }
    }

    private AttachmentType detectAttachmentType(String contentType) {

        if (contentType == null) {
            return AttachmentType.DOCUMENT;
        }

        if (contentType.startsWith("image")) {
            return AttachmentType.IMAGE;
        }

        if (contentType.startsWith("video")) {
            return AttachmentType.VIDEO;
        }

        if (contentType.startsWith("audio")) {
            return AttachmentType.AUDIO;
        }

        return AttachmentType.DOCUMENT;
    }

    private String generateStoredFileName(MultipartFile file) {

        String extension =
                StringUtils.getFilenameExtension(
                        file.getOriginalFilename()
                );

        return UUID.randomUUID() +
                (extension != null
                        ? "." + extension
                        : "");
    }

    private String saveFile(MultipartFile file, String storedFileName) {

        try {

            Path uploadPath =
                    Paths.get(uploadDirectory);

            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            Path target =
                    uploadPath.resolve(storedFileName);

            Files.copy(
                    file.getInputStream(),
                    target,
                    StandardCopyOption.REPLACE_EXISTING
            );

            return "/uploads/" + storedFileName;

        } catch (IOException ex) {

            throw new InvalidAttachmentException(
                    "Unable to upload file."
            );
        }
    }

    @Override
    public AttachmentResponse uploadAttachment(
            AttachmentRequest request
    ) {

        log.info(
                "Uploading attachment from {}",
                request.getSenderId()
        );

        MultipartFile file =
                request.getFile();

        validateAttachment(file);

        String storedFileName =
                generateStoredFileName(file);

        String fileUrl =
                saveFile(file, storedFileName);

        Attachment attachment =
                Attachment.builder()
                        .fileName(file.getOriginalFilename())
                        .storedFileName(storedFileName)
                        .fileUrl(fileUrl)
                        .contentType(file.getContentType())
                        .attachmentType(
                                detectAttachmentType(
                                        file.getContentType()
                                )
                        )
                        .fileSize(file.getSize())
                        .uploaded(true)
                        .build();

        attachment =
                attachmentRepository.save(attachment);

        log.info(
                "Attachment {} uploaded successfully.",
                attachment.getId()
        );

        return attachmentMapper.toResponse(
                attachment
        );
    }

    private final ChatMessageRepository chatMessageRepository;
    private final GroupMessageRepository groupMessageRepository;

    @Override
    @Transactional
    public AttachmentResponse getAttachment(Long attachmentId) {

        log.info("Fetching attachment {}", attachmentId);

        Attachment attachment = getAttachmentOrThrow(attachmentId);

        return attachmentMapper.toResponse(attachment);
    }

    @Override
    @Transactional
    public AttachmentResponse getChatMessageAttachment(Long messageId) {

        log.info("Fetching attachment of private message {}", messageId);

        ChatMessage message = chatMessageRepository.findById(messageId)
                .orElseThrow(() ->
                        new MessageNotFoundException(
                                "Message not found with id: " + messageId
                        ));

        Attachment attachment = attachmentRepository
                .findByChatMessage(message)
                .orElseThrow(() ->
                        new AttachmentNotFoundException(
                                "Attachment not found for message: " + messageId
                        ));

        return attachmentMapper.toResponse(attachment);
    }

    @Override
    @Transactional
    public AttachmentResponse getGroupMessageAttachment(Long groupMessageId) {

        log.info("Fetching attachment of group message {}", groupMessageId);

        GroupMessage groupMessage = groupMessageRepository.findById(groupMessageId)
                .orElseThrow(() ->
                        new MessageNotFoundException(
                                "Group message not found with id: " + groupMessageId
                        ));

        Attachment attachment = attachmentRepository
                .findByGroupMessage(groupMessage)
                .orElseThrow(() ->
                        new AttachmentNotFoundException(
                                "Attachment not found for group message: " + groupMessageId
                        ));

        return attachmentMapper.toResponse(attachment);
    }

    @Override
    @Transactional
    public List<AttachmentResponse> getAllAttachments() {

        log.info("Fetching all attachments.");

        return attachmentRepository.findAll()
                .stream()
                .map(attachmentMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public List<AttachmentResponse> getAttachmentsByType(
            AttachmentType attachmentType) {

        log.info("Fetching {} attachments.", attachmentType);

        return attachmentRepository
                .findByAttachmentType(attachmentType)
                .stream()
                .map(attachmentMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public List<AttachmentResponse> getImages() {

        log.info("Fetching image attachments.");

        return attachmentRepository
                .findByAttachmentTypeOrderByCreatedAtDesc(
                        AttachmentType.IMAGE
                )
                .stream()
                .map(attachmentMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public List<AttachmentResponse> getVideos() {

        log.info("Fetching video attachments.");

        return attachmentRepository
                .findByAttachmentTypeOrderByCreatedAtDesc(
                        AttachmentType.VIDEO
                )
                .stream()
                .map(attachmentMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public List<AttachmentResponse> getAudios() {

        log.info("Fetching audio attachments.");

        return attachmentRepository
                .findByAttachmentTypeOrderByCreatedAtDesc(
                        AttachmentType.AUDIO
                )
                .stream()
                .map(attachmentMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public List<AttachmentResponse> getDocuments() {

        log.info("Fetching document attachments.");

        return attachmentRepository
                .findByAttachmentTypeOrderByCreatedAtDesc(
                        AttachmentType.DOCUMENT
                )
                .stream()
                .map(attachmentMapper::toResponse)
                .collect(Collectors.toList());
    }

    private void deletePhysicalFile(String storedFileName) {
        try {

            Path uploadPath = Paths.get(uploadDirectory)
                    .toAbsolutePath()
                    .normalize();

            Path file = uploadPath.resolve(storedFileName).normalize();

            if (Files.exists(file)) {
                Files.delete(file);
                log.info("Deleted physical file {}", storedFileName);
            }

        } catch (IOException ex) {

            log.error("Unable to delete file {}", storedFileName, ex);

            throw new InvalidAttachmentException(
                    "Unable to delete attachment file."
            );
        }
    }

    @Override
    public void deleteAttachment(Long attachmentId) {

        log.info("Deleting attachment {}", attachmentId);

        Attachment attachment = getAttachmentOrThrow(attachmentId);

        deletePhysicalFile(
                attachment.getStoredFileName()
        );

        attachmentRepository.delete(attachment);

        log.info("Attachment {} deleted successfully.",
                attachmentId);
    }

    @Override
    public void deleteChatMessageAttachment(Long messageId) {

        log.info("Deleting attachment of message {}", messageId);

        ChatMessage message =
                chatMessageRepository.findById(messageId)
                        .orElseThrow(() ->
                                new MessageNotFoundException(
                                        "Message not found with id: " + messageId
                                ));

        attachmentRepository.findByChatMessage(message)
                .ifPresent(attachment -> {

                    deletePhysicalFile(
                            attachment.getStoredFileName()
                    );

                    attachmentRepository.delete(attachment);

                    log.info(
                            "Attachment deleted for message {}",
                            messageId
                    );
                });
    }

    @Override
    public void deleteGroupMessageAttachment(Long groupMessageId) {

        log.info("Deleting attachment of group message {}", groupMessageId);

        GroupMessage groupMessage =
                groupMessageRepository.findById(groupMessageId)
                        .orElseThrow(() ->
                                new MessageNotFoundException(
                                        "Group message not found with id: "
                                                + groupMessageId
                                ));

        attachmentRepository.findByGroupMessage(groupMessage)
                .ifPresent(attachment -> {

                    deletePhysicalFile(
                            attachment.getStoredFileName()
                    );

                    attachmentRepository.delete(attachment);

                    log.info(
                            "Attachment deleted for group message {}",
                            groupMessageId
                    );
                });
    }

    @Override
    @Transactional
    public boolean exists(Long attachmentId) {

        return attachmentRepository.existsById(
                attachmentId
        );
    }

    @Override
    @Transactional
    public long countUploadedAttachments() {

        return attachmentRepository.countByUploadedTrue();
    }

    @Override
    @Transactional
    public long countByType(
            AttachmentType attachmentType) {

        return attachmentRepository.countByAttachmentType(
                attachmentType
        );
    }
}
