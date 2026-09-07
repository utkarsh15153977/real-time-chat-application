package com.chatApplication.user_service.controller;

import com.chatApplication.user_service.dto.UpdateUserRequest;
import com.chatApplication.user_service.dto.UserResponse;
import com.chatApplication.user_service.dto.UserStatusRequest;
import com.chatApplication.user_service.exception.GlobalExceptionHandler;
import com.chatApplication.user_service.exception.UserNotFoundException;
import com.chatApplication.user_service.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UserController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService service;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void getUser_existingUser_returns200() throws Exception {
        Long userId = 1L;
        UserResponse response = new UserResponse(
                userId, "John Doe", "john@example.com", "1234567890",
                "http://img.com/profile.jpg", "Hello", true, LocalDateTime.now()
        );

        when(service.getUser(userId)).thenReturn(response);

        mockMvc.perform(get("/users/{id}", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(userId))
                .andExpect(jsonPath("$.name").value("John Doe"))
                .andExpect(jsonPath("$.email").value("john@example.com"));
    }

    @Test
    void getUser_nonExistingUser_returns404() throws Exception {
        Long userId = 99L;
        when(service.getUser(userId)).thenThrow(new UserNotFoundException("User not found with id: 99"));

        mockMvc.perform(get("/users/{id}", userId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("User not found with id: 99"));
    }

    @Test
    void updateUser_validRequest_returns200() throws Exception {
        Long userId = 1L;
        UpdateUserRequest request = new UpdateUserRequest();
        request.setName("Updated Name");
        request.setPhone("0987654321");
        request.setProfilePicture("http://img.com/new.jpg");
        request.setStatusMessage("New status");

        UserResponse response = new UserResponse(
                userId, "Updated Name", "john@example.com", "0987654321",
                "http://img.com/new.jpg", "New status", true, LocalDateTime.now()
        );

        when(service.updateUser(eq(userId), any(UpdateUserRequest.class))).thenReturn(response);

        mockMvc.perform(put("/users/{id}", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated Name"))
                .andExpect(jsonPath("$.phone").value("0987654321"));
    }

    @Test
    void updateUser_blankName_returns400() throws Exception {
        Long userId = 1L;
        UpdateUserRequest request = new UpdateUserRequest();
        request.setName("");
        request.setPhone("123");

        mockMvc.perform(put("/users/{id}", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateUser_blankPhone_returns400() throws Exception {
        Long userId = 1L;
        UpdateUserRequest request = new UpdateUserRequest();
        request.setName("Valid Name");
        request.setPhone("");

        mockMvc.perform(put("/users/{id}", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void search_returnsMatchingUsers() throws Exception {
        String name = "John";
        List<UserResponse> responses = List.of(
                new UserResponse(1L, "John Doe", "john@example.com", "123", null, null, false, null),
                new UserResponse(2L, "Johnny", "johnny@example.com", "456", null, null, false, null)
        );

        when(service.searchUser(name)).thenReturn(responses);

        mockMvc.perform(get("/users/search").param("name", name))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].name").value("John Doe"))
                .andExpect(jsonPath("$[1].name").value("Johnny"));
    }

    @Test
    void updateStatus_validRequest_returns200() throws Exception {
        Long userId = 1L;
        UserStatusRequest request = new UserStatusRequest(true);

        mockMvc.perform(put("/users/{id}/status", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().string("Status Updated"));
    }
}
