package com.chatapplication.group_chat.exception;

import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiError {
    private String exception;
    private String traceId;
    private LocalDateTime timestamp;
    private int status;
    private String error;
    private ErrorCode errorCode;
    private String message;
    private String path;
    private List<String> validationErrors;
}
