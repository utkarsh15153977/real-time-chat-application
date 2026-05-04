package com.chatbackend.chat_application_backend.exception;

public class ParticipantAlreadyExistsException extends RuntimeException{
    public ParticipantAlreadyExistsException(String message){
        super(message);
    }
}
