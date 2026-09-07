package com.chatApplication.message_service.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when S3 operations fail (presigned URL generation, connectivity, etc.).
 */
@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
public class S3ServiceException extends RuntimeException {

    public S3ServiceException(String message) {
        super(message);
    }

    public S3ServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
