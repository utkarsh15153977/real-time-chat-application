package com.chatApplication.message_service.dto;

import com.chatApplication.message_service.entity.MessageType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * DTO for outgoing chat messages broadcast via STOMP WebSocket.
 * <p>
 * Contains all message fields including media metadata.
 * This is the payload sent to /topic/room.{chatRoomId} after
 * a message is persisted to the database.
 * <p>
 * Clients use the {@code messageType} field to render the appropriate
 * UI component (text bubble, image preview, video player, etc.).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageResponseDTO {

    /** Unique message identifier (database primary key) */
    private String messageId;

    /** User ID of the message sender */
    private String senderId;

    /** User ID of the message recipient */
    private String recipientId;

    /** Chat room ID */
    private String chatRoomId;

    /** Text content of the message */
    private String content;

    /** Type of message: TEXT, IMAGE, VIDEO, AUDIO, FILE */
    private MessageType messageType;

    /** Public S3 URL of the uploaded media */
    private String mediaUrl;

    /** S3 object key for the uploaded file */
    private String fileKey;

    /** Size of the file in bytes */
    private Long fileSizeBytes;

    /** Message delivery status */
    private String status;

    /** Server-side timestamp when the message was created (epoch seconds) */
    private Instant timestamp;

    /**
     * Optional correlation ID passed through from the client.
     * <p>
     * Not persisted to the database. Returned in the response so that
     * load-testing clients (k6) can correlate sent messages with received
     * echoes for round-trip latency measurement.
     */
    private String loadTestId;
}
