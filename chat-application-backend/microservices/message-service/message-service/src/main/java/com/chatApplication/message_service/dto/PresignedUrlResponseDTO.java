package com.chatApplication.message_service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Response DTO containing the presigned S3 upload URL and metadata.
 * <p>
 * The client uses the {@code uploadUrl} to perform a direct HTTP PUT
 * to S3 with the file body. No authorization headers needed - the
 * URL itself contains the signed credentials.
 * <p>
 * The {@code fileKey} is the S3 object key that should be stored
 * in the message database to reference this media later.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PresignedUrlResponseDTO {

    /** Presigned PUT URL - client uploads directly to this URL */
    private String uploadUrl;

    /** S3 object key (path) where the file will be stored */
    private String fileKey;

    /** Public URL to access the file after upload (for serving to clients) */
    private String publicUrl;

    /** When the presigned URL expires (epoch seconds) */
    private Instant expiresAt;
}
