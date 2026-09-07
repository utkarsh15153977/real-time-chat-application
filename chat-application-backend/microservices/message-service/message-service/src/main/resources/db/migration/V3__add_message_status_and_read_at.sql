-- V3: Add delivery status tracking and read_at timestamp
-- Supports the SENT -> DELIVERED -> READ state machine
-- Includes composite index for fast bulk status updates

-- Add status column with default value for existing rows
ALTER TABLE message_table
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'SENT';

-- Add read_at timestamp for tracking when a message was read
ALTER TABLE message_table
    ADD COLUMN read_at TIMESTAMP WITH TIME ZONE NULL;

-- Composite index for fast bulk status updates by chat_room_id and status
-- Used by markChatRoomAsRead to efficiently find unread messages in a room
CREATE INDEX idx_messages_chatroom_status
    ON message_table (sender_id, receiver_id, status);

-- Index for efficient read_at queries
CREATE INDEX idx_messages_read_at
    ON message_table (read_at);
