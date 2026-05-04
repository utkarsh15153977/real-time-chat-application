package com.chatbackend.chat_application_backend.exception;

public class StatusExpiredException extends RuntimeException{
    public StatusExpiredException(String message){
        super(message);
    }
}
