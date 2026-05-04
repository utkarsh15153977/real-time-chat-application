package com.chatbackend.chat_application_backend.exception;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor

public class ErrorResponse{
    private LocalDateTime timestamp;
    private int status;
    private String error;
    private String path;
}