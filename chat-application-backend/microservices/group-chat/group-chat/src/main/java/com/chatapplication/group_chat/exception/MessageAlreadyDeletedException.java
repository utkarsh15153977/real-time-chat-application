package com.chatapplication.group_chat.exception;

public class MessageAlreadyDeletedException extends RuntimeException {
    public MessageAlreadyDeletedException(String message) {
        super(message);
    }
    public MessageAlreadyDeletedException() {
        super("This message has already been deleted.");
    }
}
