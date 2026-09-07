package com.chatApplication.message_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for generating a presigned S3 upload URL.
 * <p>
 * The client sends this DTO when it wants to upload media to S3.
 * The server validates the request, generates a presigned PUT URL,
 * and returns it so the client can upload directly to S3.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PresignedUrlRequestDTO {

    /** Original filename including extension (e.g., "photo.jpg") */
    @NotBlank(message = "File name is required")
    private String fileName;

    /** MIME type of the file (e.g., "image/jpeg", "video/mp4") */
    @NotBlank(message = "Content type is required")
    private String contentType;

    /** File size in bytes - used for validation against max upload size */
    @NotNull(message = "File size is required")
    @Positive(message = "File size must be positive")
    private Long fileSizeBytes;

    /** Target chat room ID where this media will be shared */
    @NotBlank(message = "Chat room ID is required")
    private String chatRoomId;
}
