package com.chatApplication.message_service.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when a message request violates business rules.
 * Examples: missing mediaUrl for non-TEXT messages, invalid message type, etc.
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class MessageValidationException extends RuntimeException {

    public MessageValidationException(String message) {
        super(message);
    }

    public MessageValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
