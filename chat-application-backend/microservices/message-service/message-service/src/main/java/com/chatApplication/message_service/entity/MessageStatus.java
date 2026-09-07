package com.chatApplication.message_service.entity;

/**
 * Message lifecycle state machine.
 * <p>
 * States and transitions:
 * <pre>
 *   SENT ──────> DELIVERED ──────> READ
 *     │                              ^
 *     └────────> FAILED ────────────┘ (manual retry/resend)
 * </pre>
 * <p>
 * - SENT: Message persisted to DB, sent via WebSocket to receiver
 * - DELIVERED: Receiver's client acknowledged receipt (DELIVERED_ACK)
 * - READ: Receiver's client acknowledged viewing (READ_ACK)
 * - FAILED: Delivery attempt failed (network error, timeout, etc.)
 * <p>
 * Transitions are validated to prevent illegal state changes
 * (e.g., cannot go from READ back to SENT).
 */
public enum MessageStatus {
    /** Initial state: message saved and sent via WebSocket */
    SENT,

    /** Receiver's client sent DELIVERED_ACK confirming receipt */
    DELIVERED,

    /** Receiver's client sent READ_ACK confirming they viewed the message */
    READ,

    /** Delivery failed (used for retry/resend logic) */
    FAILED
}
