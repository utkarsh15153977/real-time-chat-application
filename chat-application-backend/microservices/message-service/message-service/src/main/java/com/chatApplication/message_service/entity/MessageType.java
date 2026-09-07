package com.chatApplication.message_service.entity;

/**
 * Enumerates the supported message types in the chat platform.
 * <p>
 * Each type corresponds to a different content delivery strategy:
 * - TEXT: Plain text message, no media attachment
 * - IMAGE: Image file (jpg, png, gif, webp) with thumbnail generation
 * - VIDEO: Video file (mp4, webm) with streaming support
 * - AUDIO: Audio file (mp3, ogg, wav) with playback support
 * - FILE: Generic file attachment (pdf, doc, zip, etc.)
 * <p>
 * The messageType is stored in the database and used by clients
 * to render appropriate UI components (image preview, video player,
 * audio player, file download card, etc.)
 */
public enum MessageType {
    TEXT,
    IMAGE,
    VIDEO,
    AUDIO,
    FILE
}
