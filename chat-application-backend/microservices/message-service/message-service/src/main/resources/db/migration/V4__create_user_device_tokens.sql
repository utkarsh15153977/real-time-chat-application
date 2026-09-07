-- V4: Create user_device_tokens table for FCM push notification device registration
CREATE TABLE user_device_tokens (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL,
    fcm_token TEXT NOT NULL UNIQUE,
    device_type VARCHAR(50) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_user_device_tokens_user_id ON user_device_tokens (user_id);
