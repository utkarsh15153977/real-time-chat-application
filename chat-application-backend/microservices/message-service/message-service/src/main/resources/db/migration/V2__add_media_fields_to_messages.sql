-- V2: Add media fields to message_table for rich content support
-- Supports TEXT, IMAGE, VIDEO, AUDIO, and FILE message types
-- Uses ALTER TABLE for safe migration on existing data

-- Add message_type column with default value for existing rows
ALTER TABLE message_table
    ADD COLUMN message_type VARCHAR(30) NOT NULL DEFAULT 'TEXT';

-- Add media_url for the public S3 URL of the uploaded file
ALTER TABLE message_table
    ADD COLUMN media_url TEXT;

-- Add file_key for the S3 object key (used for deletion/reference)
ALTER TABLE message_table
    ADD COLUMN file_key VARCHAR(500);

-- Add file size tracking for display and validation
ALTER TABLE message_table
    ADD COLUMN file_size_bytes BIGINT;

-- Index on message_type for efficient filtering (e.g., "show only images")
CREATE INDEX idx_message_table_type ON message_table (message_type);
