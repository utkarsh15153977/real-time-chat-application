package com.chatApplication.notification_service.exception;

/**
 * Thrown when an authenticated user attempts to access or modify
 * a resource they do not own.
 */
public class AccessDeniedException extends RuntimeException {
    public AccessDeniedException(String message) {
        super(message);
    }
}
