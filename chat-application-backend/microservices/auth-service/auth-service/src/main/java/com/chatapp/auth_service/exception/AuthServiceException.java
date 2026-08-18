package com.chatapp.auth_service.exception;

public class AuthServiceException extends RuntimeException{
    private final String errorCode;
    private final int status;

    public AuthServiceException(String message, String errorCode, int status) {
        super(message);
        this.errorCode = errorCode;
        this.status = status;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public int getStatus() {
        return status;
    }
}
