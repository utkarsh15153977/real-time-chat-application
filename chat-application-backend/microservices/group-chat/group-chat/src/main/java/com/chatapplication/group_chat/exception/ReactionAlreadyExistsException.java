package com.chatapplication.group_chat.exception;

public class ReactionAlreadyExistsException extends RuntimeException {
    public ReactionAlreadyExistsException(String message) {
        super(message);
    }
}
