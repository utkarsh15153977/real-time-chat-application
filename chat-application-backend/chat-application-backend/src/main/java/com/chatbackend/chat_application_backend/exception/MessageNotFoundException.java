package com.chatbackend.chat_application_backend.exception;

public class MessageNotFoundException extends RuntimeException{
    public MessageNotFoundException(String message){
        super(message);
    }
}
