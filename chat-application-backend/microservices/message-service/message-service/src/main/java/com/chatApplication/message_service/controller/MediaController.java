package com.chatApplication.message_service.controller;

import com.chatApplication.message_service.dto.PresignedUrlRequestDTO;
import com.chatApplication.message_service.dto.PresignedUrlResponseDTO;
import com.chatApplication.message_service.service.MediaService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for media upload operations.
 * <p>
 * Provides a single endpoint for generating presigned S3 upload URLs.
 * The actual file upload happens directly from the client to S3 - this
 * controller only handles URL generation and validation.
 * <p>
 * Endpoint: POST /api/v1/media/presigned-url
 * <p>
 * Request flow:
 *   1. Client sends file metadata (name, type, size, chat room)
 *   2. Server validates the request
 *   3. Server generates a presigned PUT URL (15-minute validity)
 *   4. Client uses the URL to upload directly to S3
 *   5. Client references the fileKey in subsequent messages
 * <p>
 * Security:
 * - CSRF disabled (REST API)
 * - Endpoint is publicly accessible (auth handled by API Gateway)
 * - File size and MIME type are validated server-side
 * - Presigned URLs are time-limited and operation-scoped
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/media")
@ConditionalOnExpression("'${aws.s3.access-key:}' != ''")
public class MediaController {

    private final MediaService mediaService;

    public MediaController(MediaService mediaService) {
        this.mediaService = mediaService;
    }

    /**
     * Generates a presigned S3 PUT URL for uploading media files.
     * <p>
     * The presigned URL allows the client to upload directly to S3
     * without exposing server credentials or routing file bytes
     * through the server.
     *
     * @param request file metadata (fileName, contentType, fileSizeBytes, chatRoomId)
     * @param userId  authenticated user ID (from request header or JWT)
     * @return presigned URL response with upload URL, file key, and public URL
     */
    @PostMapping("/presigned-url")
    public ResponseEntity<PresignedUrlResponseDTO> generatePresignedUrl(
            @Valid @RequestBody PresignedUrlRequestDTO request,
            @RequestHeader(value = "X-User-Id", defaultValue = "anonymous") String userId) {

        log.info("Presigned URL request: user={}, file={}, type={}, size={} bytes, chatRoom={}",
                userId,
                request.getFileName(),
                request.getContentType(),
                request.getFileSizeBytes(),
                request.getChatRoomId());

        PresignedUrlResponseDTO response =
                mediaService.generatePresignedUploadUrl(request, userId);

        log.info("Presigned URL generated: user={}, key={}, expires={}",
                userId, response.getFileKey(), response.getExpiresAt());

        return ResponseEntity.ok(response);
    }
}
