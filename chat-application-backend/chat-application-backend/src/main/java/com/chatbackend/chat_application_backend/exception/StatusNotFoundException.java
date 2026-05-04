package com.chatbackend.chat_application_backend.exception;

public class StatusNotFoundException extends RuntimeException{
    public StatusNotFoundException(String message){
        super(message);
    }
}
