package com.chatApplication.chat_service.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(DuplicateMemberException.class)
    public ResponseEntity<ErrorResponse>
            handleDuplicateMembersException(DuplicateMemberException ex, HttpServletRequest request) {
        ErrorResponse response=
                new ErrorResponse(
                        LocalDateTime.now(),
                        HttpStatus.CONFLICT.value(),
                        "Duplicate member not allowed",
                        ex.getMessage(),
                        "/chat"
                );
        return new ResponseEntity<>(
                response,
                HttpStatus.CONFLICT);
    }

    @ExceptionHandler(GroupNotFoundException.class)
    public ResponseEntity<ErrorResponse>
            handleGroupNotFoundException(GroupNotFoundException ex, HttpServletRequest request) {
        ErrorResponse response=new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.NOT_FOUND.value(),
                "Group not found.",
                ex.getMessage(),
                "/chat"
        );
        return new ResponseEntity<>(
                response,
                HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(ChatNotFoundException.class)
    public ResponseEntity<ErrorResponse>
            handleChatNotFoundException(ChatNotFoundException ex, HttpServletRequest request) {
        ErrorResponse response=new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.NOT_FOUND.value(),
                "Chat not found.",
                ex.getMessage(),
                "/chat"
        );

        return new ResponseEntity<>(
                response,
                HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(MemberNotFoundException.class)
    public ResponseEntity<ErrorResponse>
            handleMemberNotFoundException(MemberNotFoundException ex, HttpServletRequest request) {
        ErrorResponse response=new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.NOT_FOUND.value(),
                "Member not found.",
                ex.getMessage(),
                "/chat"
        );
        return new ResponseEntity<>(
                response,
                HttpStatus.NOT_FOUND);
    }
}
