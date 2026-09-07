package com.chatApplication.message_service.service;

import com.chatApplication.message_service.dto.PresignedUrlRequestDTO;
import com.chatApplication.message_service.dto.PresignedUrlResponseDTO;
import com.chatApplication.message_service.exception.MediaValidationException;
import com.chatApplication.message_service.exception.S3ServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Production-grade media upload service using S3 Presigned URLs.
 * <p>
 * Presigned URL architecture:
 * <pre>
 *   Client                    Server (message-service)              AWS S3
 *     │                              │                               │
 *     │  POST /api/v1/media/...      │                               │
 *     │  {fileName, contentType,     │                               │
 *     │   fileSizeBytes, chatRoomId} │                               │
 *     │ ──────────────────────────>  │                               │
 *     │                              │  Validate request             │
 *     │                              │  Generate S3 key              │
 *     │                              │  Create presigned PUT URL     │
 *     │  {uploadUrl, fileKey,        │                               │
 *     │   publicUrl, expiresAt}      │                               │
 *     │ <──────────────────────────  │                               │
 *     │                              │                               │
 *     │  PUT {uploadUrl}             │                               │
 *     │  Body: file bytes            │                               │
 *     │ ──────────────────────────────────────────────────────────>  │
 *     │  200 OK                      │                               │
 *     │ <──────────────────────────────────────────────────────────  │
 *     │                              │                               │
 *     │  Send message with fileKey   │                               │
 *     │ ──────────────────────────>  │                               │
 * </pre>
 * <p>
 * Security considerations:
 * - Presigned URLs are time-limited (15 minutes default)
 * - URLs contain the S3 key path - no secrets are exposed
 * - File size and MIME type are validated server-side before URL generation
 * - The presigned URL is only valid for PUT operations
 * - Bucket ACL/policy must allow the presigned URL uploads
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MediaServiceImpl implements MediaService {

    private final S3Presigner s3Presigner;

    @Value("${aws.s3.bucket-name}")
    private String bucketName;

    @Value("${aws.s3.region}")
    private String region;

    @Value("${aws.s3.endpoint:}")
    private String endpointOverride;

    @Value("${aws.s3.presign-expiry-minutes:15}")
    private int presignExpiryMinutes;

    @Value("${aws.s3.max-file-size-bytes:52428800}")
    private long maxFileSizeBytes;

    /** Allowed MIME type prefixes for media uploads */
    private static final Set<String> ALLOWED_MIME_PREFIXES = Set.of(
            "image/",
            "video/",
            "audio/"
    );

    /** Regex pattern for safe S3 key characters */
    private static final Pattern SAFE_KEY_PATTERN =
            Pattern.compile("^[a-zA-Z0-9._\\-/]+$");

    /**
     * {@inheritDoc}
     * <p>
     * Generates a unique S3 object key in the format:
     * {@code chats/{chatRoomId}/{uuid}-{sanitizedFileName}}
     * <p>
     * The presigned URL is valid for 15 minutes (configurable) and
     * allows only PUT operations with the specified content type.
     */
    @Override
    public PresignedUrlResponseDTO generatePresignedUploadUrl(
            PresignedUrlRequestDTO request,
            String userId) {

        // 1. Validate file size
        validateFileSize(request.getFileSizeBytes());

        // 2. Validate MIME type
        validateContentType(request.getContentType());

        // 3. Generate unique S3 object key
        String fileKey = generateS3Key(
                request.getChatRoomId(),
                request.getFileName());

        // 4. Build S3 PutObjectRequest with metadata
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(fileKey)
                .contentType(request.getContentType())
                .metadata("uploader-id", userId)
                .metadata("chat-room-id", request.getChatRoomId())
                .metadata("original-filename", request.getFileName())
                .build();

        // 5. Create presigned URL (time-limited PUT access)
        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(presignExpiryMinutes))
                .putObjectRequest(putObjectRequest)
                .build();

        PresignedPutObjectRequest presignedRequest =
                s3Presigner.presignPutObject(presignRequest);

        // 6. Build public URL for serving the file after upload
        String publicUrl = buildPublicUrl(fileKey);

        log.info("Generated presigned URL: user={}, file={}, size={}, expires={}",
                userId, fileKey, request.getFileSizeBytes(),
                presignedRequest.expiration());

        return PresignedUrlResponseDTO.builder()
                .uploadUrl(presignedRequest.url().toString())
                .fileKey(fileKey)
                .publicUrl(publicUrl)
                .expiresAt(presignedRequest.expiration().toInstant())
                .build();
    }

    /**
     * Validates that the file size does not exceed the configured maximum.
     *
     * @param fileSizeBytes file size in bytes
     * @throws MediaValidationException if file is too large
     */
    private void validateFileSize(Long fileSizeBytes) {
        if (fileSizeBytes == null || fileSizeBytes <= 0) {
            throw new MediaValidationException(
                    "File size must be a positive number");
        }
        if (fileSizeBytes > maxFileSizeBytes) {
            throw new MediaValidationException(
                    String.format("File size %d bytes exceeds maximum allowed size of %d bytes (%dMB)",
                            fileSizeBytes, maxFileSizeBytes,
                            maxFileSizeBytes / (1024 * 1024)));
        }
    }

    /**
     * Validates that the MIME type is in the allowed media types list.
     * Only image/*, video/*, and audio/* are permitted.
     *
     * @param contentType the MIME type to validate
     * @throws MediaValidationException if the MIME type is not allowed
     */
    private void validateContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            throw new MediaValidationException("Content type is required");
        }

        boolean allowed = ALLOWED_MIME_PREFIXES.stream()
                .anyMatch(contentType::startsWith);

        if (!allowed) {
            throw new MediaValidationException(
                    String.format("Content type '%s' is not allowed. "
                                    + "Supported types: images (image/*), videos (video/*), audio (audio/*)",
                            contentType));
        }
    }

    /**
     * Generates a unique S3 object key for the uploaded file.
     * <p>
     * Key format: chats/{chatRoomId}/{uuid}-{sanitizedFileName}
     * The UUID ensures uniqueness even for files with the same name.
     *
     * @param chatRoomId the chat room ID
     * @param fileName   the original file name
     * @return unique S3 object key
     */
    private String generateS3Key(String chatRoomId, String fileName) {
        // Sanitize the filename to remove path traversal and special chars
        String sanitizedFileName = sanitizeFileName(fileName);
        String uuid = UUID.randomUUID().toString();

        return String.format("chats/%s/%s-%s",
                chatRoomId, uuid, sanitizedFileName);
    }

    /**
     * Sanitizes a filename for safe use in S3 keys.
     * Preserves extensions for proper content-type handling.
     *
     * @param fileName the original filename
     * @return sanitized filename safe for S3 keys
     */
    private String sanitizeFileName(String fileName) {
        // Replace path separators and special characters
        String sanitized = fileName
                .replaceAll("[^a-zA-Z0-9._\\-]", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_|_$", "");

        // Ensure the filename is not empty and has reasonable length
        if (sanitized.isEmpty() || sanitized.length() > 200) {
            // Fallback: use a generic name with the extension preserved
            String ext = "";
            int dotIndex = fileName.lastIndexOf('.');
            if (dotIndex > 0) {
                ext = fileName.substring(dotIndex);
            }
            sanitized = "media" + ext;
        }

        return sanitized;
    }

    /**
     * Builds the public URL for accessing the uploaded file.
     * <p>
     * For real AWS S3:
     *   https://{bucket}.s3.{region}.amazonaws.com/{key}
     * <p>
     * For custom endpoints (MinIO):
     *   {endpoint}/{bucket}/{key}
     *
     * @param fileKey the S3 object key
     * @return the public URL for the file
     */
    private String buildPublicUrl(String fileKey) {
        if (endpointOverride != null && !endpointOverride.isBlank()) {
            // Custom endpoint (MinIO, LocalStack)
            return String.format("%s/%s/%s",
                    endpointOverride.replaceAll("/$", ""),
                    bucketName,
                    fileKey);
        }
        // Standard AWS S3 URL
        return String.format("https://%s.s3.%s.amazonaws.com/%s",
                bucketName, region, fileKey);
    }
}
