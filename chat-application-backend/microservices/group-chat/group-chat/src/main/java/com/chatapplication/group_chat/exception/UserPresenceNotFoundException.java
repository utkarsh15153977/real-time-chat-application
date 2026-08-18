package com.chatapplication.group_chat.exception;

public class UserPresenceNotFoundException extends RuntimeException {
    public UserPresenceNotFoundException(String message) {
        super(message);
    }
}
