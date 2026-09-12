package com.chatApplication.message_service.dto;

import com.chatApplication.message_service.entity.MessageType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for incoming chat messages via STOMP WebSocket or REST API.
 * <p>
 * Supports both text and media messages. When messageType is not TEXT,
 * mediaUrl and fileKey must be provided (the client has already uploaded
 * the file to S3 via the presigned URL endpoint).
 * <p>
 * Validation rules:
 * - senderId and recipientId are always required
 * - content is required for TEXT messages
 * - mediaUrl and fileKey are required for IMAGE, VIDEO, AUDIO, FILE messages
 * - chatRoomId is required for room-based messaging
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageRequestDTO {

    /** User ID of the message sender */
    @NotBlank(message = "Sender ID is required")
    private String senderId;

    /** User ID of the message recipient */
    @NotBlank(message = "Recipient ID is required")
    private String recipientId;

    /** Chat room ID for room-based messaging */
    private String chatRoomId;

    /** Text content of the message (required for TEXT type) */
    private String content;

    /** Type of message: TEXT, IMAGE, VIDEO, AUDIO, FILE */
    @NotNull(message = "Message type is required")
    private MessageType messageType;

    /** Public S3 URL of the uploaded media (required for non-TEXT types) */
    private String mediaUrl;

    /** S3 object key for the uploaded file (required for non-TEXT types) */
    private String fileKey;

    /** Size of the file in bytes */
    private Long fileSizeBytes;

    /**
     * Optional correlation ID passed through from the client.
     * <p>
     * Not persisted to the database. Returned in the response DTO
     * so that load-testing clients (k6) can correlate sent messages
     * with received echoes for round-trip latency measurement.
     */
    private String loadTestId;
}
