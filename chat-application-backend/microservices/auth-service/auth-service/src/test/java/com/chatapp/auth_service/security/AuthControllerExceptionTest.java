package com.chatapp.auth_service.security;

import com.chatapp.auth_service.config.TestConfig;
import com.chatapp.auth_service.dto.LoginRequest;
import com.chatapp.auth_service.dto.RegisterRequest;
import com.chatapp.auth_service.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@Import(TestConfig.class)
public class AuthControllerExceptionTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private RegisterRequest validRegisterRequest;
    private RegisterRequest existingEmailRequest;

    @BeforeEach
    void setUp() {

        // Clean users before every test
        userRepository.deleteAll();

        validRegisterRequest = new RegisterRequest();
        validRegisterRequest.setEmail("newuser@example.com");
        validRegisterRequest.setPassword("Password123!");
        validRegisterRequest.setName("New User");
        validRegisterRequest.setPhone("+1234567890");

        existingEmailRequest = new RegisterRequest();
        existingEmailRequest.setEmail("test@example.com");
        existingEmailRequest.setPassword("Password123!");
        existingEmailRequest.setName("Test User");
        existingEmailRequest.setPhone("+1234567890");
    }

    @Test
    void testSuccessfulRegistration_Returns200() throws Exception {

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJsonString(validRegisterRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").doesNotExist())
                .andExpect(jsonPath("$.userId").exists())
                .andExpect(jsonPath("$.email")
                        .value(validRegisterRequest.getEmail()))
                .andExpect(jsonPath("$.requiresTwoFactor")
                        .value(true))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void testEmailAlreadyExists_Returns409() throws Exception {

        // First registration - success
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJsonString(existingEmailRequest)))
                .andExpect(status().isOk());

        // Second registration - should fail
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJsonString(existingEmailRequest)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error")
                        .value("Email Already Exists"))
                .andExpect(jsonPath("$.errorCode")
                        .value("EMAIL_ALREADY_EXISTS"));
    }

    @Test
    void testInvalidCredentials_Returns401() throws Exception {

        // Register a fresh user
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJsonString(validRegisterRequest)))
                .andExpect(status().isOk());

        // Login using wrong password
        LoginRequest request = new LoginRequest();
        request.setEmail("newuser@example.com");
        request.setPassword("WrongPassword123");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJsonString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error")
                        .value("Invalid Credentials"))
                .andExpect(jsonPath("$.errorCode")
                        .value("INVALID_CREDENTIALS"));
    }

    @Test
    void testUserNotFound_Returns404() throws Exception {

        LoginRequest request = new LoginRequest();
        request.setEmail("nonexistent@example.com");
        request.setPassword("WrongPassword");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJsonString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error")
                        .value("User Not Found"))
                .andExpect(jsonPath("$.errorCode")
                        .value("USER_NOT_FOUND"));
    }

    @Test
    void testWeakPassword_Returns400() throws Exception {

        RegisterRequest request = new RegisterRequest();
        request.setEmail("test2@example.com");
        request.setPassword("weak");
        request.setName("Test User");
        request.setPhone("+1234567890");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJsonString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error")
                        .value("Bad Request"))
                .andExpect(jsonPath("$.errorCode")
                        .value("INVALID_ARGUMENT"));
    }

    @Test
    void testMissingEmail_Returns400() throws Exception {

        RegisterRequest request = new RegisterRequest();
        request.setPassword("Password123!");
        request.setName("Test User");
        request.setPhone("+1234567890");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJsonString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testInvalidEmailFormat_Returns400() throws Exception {

        RegisterRequest request = new RegisterRequest();
        request.setEmail("invalid-email");
        request.setPassword("Password123!");
        request.setName("Test User");
        request.setPhone("+1234567890");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJsonString(request)))
                .andExpect(status().isBadRequest());
    }

    private String asJsonString(Object obj) {

        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}