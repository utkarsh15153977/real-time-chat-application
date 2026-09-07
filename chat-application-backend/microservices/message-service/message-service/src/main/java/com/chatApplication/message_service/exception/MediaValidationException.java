package com.chatApplication.message_service.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when a media file violates size or type constraints.
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class MediaValidationException extends RuntimeException {

    public MediaValidationException(String message) {
        super(message);
    }

    public MediaValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
