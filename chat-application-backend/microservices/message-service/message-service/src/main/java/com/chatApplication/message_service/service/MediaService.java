package com.chatApplication.message_service.service;

import com.chatApplication.message_service.dto.PresignedUrlRequestDTO;
import com.chatApplication.message_service.dto.PresignedUrlResponseDTO;

/**
 * Service interface for media upload operations using S3 Presigned URLs.
 * <p>
 * Presigned URL flow:
 *   1. Client calls POST /api/v1/media/presigned-url with file metadata
 *   2. Server validates the request (file size, MIME type)
 *   3. Server generates a unique S3 key and presigned PUT URL
 *   4. Server returns the presigned URL to the client
 *   5. Client performs HTTP PUT directly to S3 with the file body
 *   6. Client sends the fileKey with the next message to reference the upload
 * <p>
 * This approach:
 * - Offloads file transfer bandwidth from the server to S3
 * - Avoids storing file bytes in server memory
 * - Works with any file size up to the configured limit
 * - Keeps server credentials secure (never exposed to client)
 */
public interface MediaService {

    /**
     * Generates a presigned S3 PUT URL for uploading media.
     *
     * @param request file metadata (name, type, size, chat room)
     * @param userId  the authenticated user requesting the upload
     * @return presigned URL and metadata for direct S3 upload
     */
    PresignedUrlResponseDTO generatePresignedUploadUrl(
            PresignedUrlRequestDTO request,
            String userId);
}
