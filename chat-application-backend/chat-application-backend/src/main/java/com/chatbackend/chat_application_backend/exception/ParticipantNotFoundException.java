package com.chatbackend.chat_application_backend.exception;

public class ParticipantNotFoundException extends RuntimeException{
    public ParticipantNotFoundException(String message){
        super(message);
    }
}