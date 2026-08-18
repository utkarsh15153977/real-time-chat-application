package com.chatapplication.group_chat.exception;

public class MessageAlreadyEditedException extends RuntimeException {
    public MessageAlreadyEditedException(String message) {
        super(message);
    }

    public MessageAlreadyEditedException() {
        super("This message has already been edited.");
    }
}
