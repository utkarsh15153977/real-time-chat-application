package com.chatbackend.chat_application_backend.exception;

public class MessageDeliveryException extends RuntimeException{
    public MessageDeliveryException(String message){
        super(message);
    }
}
