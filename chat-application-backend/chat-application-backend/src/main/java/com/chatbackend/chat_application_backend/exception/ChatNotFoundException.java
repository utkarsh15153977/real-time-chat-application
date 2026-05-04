package com.chatbackend.chat_application_backend.exception;

public class ChatNotFoundException extends RuntimeException{
    public ChatNotFoundException(String message){
        super(message);
    }
}
