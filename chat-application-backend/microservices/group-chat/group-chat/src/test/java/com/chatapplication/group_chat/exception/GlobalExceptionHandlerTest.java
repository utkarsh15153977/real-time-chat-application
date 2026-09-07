package com.chatapplication.group_chat.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;
    private HttpServletRequest request;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
        request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/test");
    }

    @Test
    void handleResourceNotFound_shouldReturn404() {
        ResourceNotFoundException ex = new ResourceNotFoundException("Not found");

        ResponseEntity<ApiError> response = handler.handleResourceNotFound(ex, request);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Not found", response.getBody().getMessage());
    }

    @Test
    void handleBadRequest_shouldReturn400() {
        BadRequestException ex = new BadRequestException("Bad request");

        ResponseEntity<ApiError> response = handler.handleBadRequest(ex, request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Bad request", response.getBody().getMessage());
    }

    @Test
    void handleConflict_shouldReturn409() {
        ConflictException ex = new ConflictException("Conflict");

        ResponseEntity<ApiError> response = handler.handleConflict(ex, request);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Conflict", response.getBody().getMessage());
    }

    @Test
    void handleUnauthorized_shouldReturn401() {
        UnauthorizedException ex = new UnauthorizedException("Unauthorized");

        ResponseEntity<ApiError> response = handler.handleUnauthorized(ex, request);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Unauthorized", response.getBody().getMessage());
    }

    @Test
    void handleForbidden_shouldReturn403() {
        ForbiddenException ex = new ForbiddenException("Forbidden");

        ResponseEntity<ApiError> response = handler.handleForbidden(ex, request);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Forbidden", response.getBody().getMessage());
    }

    @Test
    void handleValidation_shouldReturn400() {
        BindingResult bindingResult = mock(BindingResult.class);
        FieldError fieldError = new FieldError("object", "field", "must not be blank");
        when(bindingResult.getFieldErrors()).thenReturn(List.of(fieldError));

        MethodArgumentNotValidException ex =
                new MethodArgumentNotValidException(null, bindingResult);

        ResponseEntity<ApiError> response = handler.handleValidation(ex, request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Validation failed", response.getBody().getMessage());
        assertNotNull(response.getBody().getValidationErrors());
        assertEquals(1, response.getBody().getValidationErrors().size());
    }

    @Test
    void handleValidation_withMultipleErrors_shouldReturnAll() {
        BindingResult bindingResult = mock(BindingResult.class);
        FieldError error1 = new FieldError("object", "name", "must not be blank");
        FieldError error2 = new FieldError("object", "email", "must be valid");
        when(bindingResult.getFieldErrors()).thenReturn(List.of(error1, error2));

        MethodArgumentNotValidException ex =
                new MethodArgumentNotValidException(null, bindingResult);

        ResponseEntity<ApiError> response = handler.handleValidation(ex, request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(2, response.getBody().getValidationErrors().size());
    }

    @Test
    void handleConstraintViolation_shouldReturn400() {
        ConstraintViolationException ex = new ConstraintViolationException("Invalid value", Set.of());

        ResponseEntity<ApiError> response = handler.handleConstraintViolation(ex, request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Invalid value", response.getBody().getMessage());
    }

    @Test
    void handleFileUpload_shouldReturn500() {
        FileUploadException ex = new FileUploadException("Upload failed");

        ResponseEntity<ApiError> response = handler.handleFileUpload(ex, request);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Upload failed", response.getBody().getMessage());
    }

    @Test
    void handleException_shouldReturn500() {
        Exception ex = new Exception("Something went wrong");

        ResponseEntity<ApiError> response = handler.handleException(ex, request);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Something went wrong", response.getBody().getMessage());
    }

    @Test
    void allHandlers_shouldReturnCorrectPath() {
        ResourceNotFoundException ex = new ResourceNotFoundException("Not found");

        ResponseEntity<ApiError> response = handler.handleResourceNotFound(ex, request);

        assertNotNull(response.getBody());
        assertEquals("/api/test", response.getBody().getPath());
    }

    @Test
    void allHandlers_shouldReturnCorrectTimestamp() {
        ResourceNotFoundException ex = new ResourceNotFoundException("Not found");

        ResponseEntity<ApiError> response = handler.handleResourceNotFound(ex, request);

        assertNotNull(response.getBody());
        assertNotNull(response.getBody().getTimestamp());
    }
}
