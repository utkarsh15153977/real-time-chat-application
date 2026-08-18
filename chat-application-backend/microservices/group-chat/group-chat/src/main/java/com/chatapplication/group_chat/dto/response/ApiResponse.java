package com.chatapplication.group_chat.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {
    /**
     * Indicates whether the request was successful.
     */
    private boolean success;

    /**
     * HTTP Status Code.
     * Example: 200, 201, 400, 404, 500
     */
    private int status;

    /**
     * Human-readable message.
     */
    private String message;

    /**
     * Response payload.
     */
    private T data;

    /**
     * API endpoint path.
     */
    private String path;

    /**
     * Response timestamp.
     */
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();

    public static <T> ApiResponse<T> success(String message, T data, String path) {
        ApiResponse<T> response = new ApiResponse<>();
        response.setSuccess(true);
        response.setStatus(200);
        response.setMessage(message);
        response.setData(data);
        response.setPath(path);
        return response;
    }

    public static <T> ApiResponse<T> error(int status, String message, String path) {
        ApiResponse<T> response = new ApiResponse<>();
        response.setSuccess(false);
        response.setStatus(status);
        response.setMessage(message);
        response.setPath(path);
        return response;
    }

}
