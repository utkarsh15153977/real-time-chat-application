-- V3: Add delivery status tracking and read_at timestamp
-- Supports the SENT -> DELIVERED -> READ state machine
-- Includes composite indexes for fast status updates

-- Add status column
ALTER TABLE message_table
    ADD COLUMN IF NOT EXISTS status VARCHAR(20) NOT NULL DEFAULT 'SENT';

-- Add read_at timestamp
ALTER TABLE message_table
    ADD COLUMN IF NOT EXISTS read_at TIMESTAMP WITH TIME ZONE NULL;

-- Add delivered_at timestamp
ALTER TABLE message_table
    ADD COLUMN IF NOT EXISTS delivered_at TIMESTAMP WITH TIME ZONE NULL;

-- Create indexes
CREATE INDEX IF NOT EXISTS idx_messages_chatroom_status
    ON message_table (sender_id, receiver_id, status);

CREATE INDEX IF NOT EXISTS idx_messages_read_at
    ON message_table (read_at);

CREATE INDEX IF NOT EXISTS idx_messages_receiver_status
    ON message_table (receiver_id, status);