-- V5: Add delivered timestamp and receiver status index

ALTER TABLE message_table
    ADD COLUMN IF NOT EXISTS delivered_at TIMESTAMP WITH TIME ZONE NULL;

CREATE INDEX IF NOT EXISTS idx_messages_receiver_status
    ON message_table (receiver_id, status);
