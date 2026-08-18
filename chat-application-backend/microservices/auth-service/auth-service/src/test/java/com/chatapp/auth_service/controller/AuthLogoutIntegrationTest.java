package com.chatapp.auth_service.controller;

import com.chatapp.auth_service.entity.BlacklistedToken;
import com.chatapp.auth_service.entity.User;
import com.chatapp.auth_service.repository.BlacklistedTokenRepository;
import com.chatapp.auth_service.repository.UserRepository;
import com.chatapp.auth_service.security.JwtUtil;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthLogoutIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BlacklistedTokenRepository blacklistedTokenRepository;


    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
    }


    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }


    // =========================================================
    // LOGOUT SUCCESS
    // =========================================================

    @Test
    void logoutShouldBlacklistValidJwt()
            throws Exception {

        User user = User.builder()
                .name("Logout Test User")
                .email("logout-test@gmail.com")
                .password("Password@123")
                .phone("9999999999")
                .emailVerified(true)
                .phoneVerified(true)
                .twoFactorEnabled(false)
                .online(false)
                .build();

        User savedUser = userRepository.save(user);

        String token = jwtUtil.generateToken(savedUser);

        mockMvc.perform(
                        post("/api/auth/logout")
                                .header(
                                        "Authorization",
                                        "Bearer " + token
                                )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                )
                .andExpect(status().isOk())
                .andExpect(
                        content()
                                .string("Logout successful")
                );

        Optional<BlacklistedToken> blacklistedToken =
                blacklistedTokenRepository.findByToken(token);

        assertTrue(
                blacklistedToken.isPresent(),
                "JWT should be stored in blacklist after logout"
        );

        assertEquals(
                token,
                blacklistedToken.get().getToken()
        );

        assertNotNull(
                blacklistedToken.get().getBlacklistedAt()
        );
    }


    // =========================================================
    // BLACKLISTED JWT MUST BE REJECTED
    // =========================================================

    @Test
    void blacklistedJwtShouldNotAuthenticateAgain()
            throws Exception {

        User user = User.builder()
                .name("Blacklist Test User")
                .email("blacklist-test@gmail.com")
                .password("Password@123")
                .phone("8888888888")
                .emailVerified(true)
                .phoneVerified(true)
                .twoFactorEnabled(false)
                .online(false)
                .build();

        User savedUser = userRepository.save(user);

        String token = jwtUtil.generateToken(savedUser);


        // -----------------------------------------------------
        // First request: logout
        // -----------------------------------------------------

        mockMvc.perform(
                        post("/api/auth/logout")
                                .header(
                                        "Authorization",
                                        "Bearer " + token
                                )
                )
                .andExpect(status().isOk())
                .andExpect(
                        content()
                                .string("Logout successful")
                );


        // -----------------------------------------------------
        // Confirm token exists in blacklist
        // -----------------------------------------------------

        assertTrue(
                blacklistedTokenRepository
                        .findByToken(token)
                        .isPresent()
        );


        // -----------------------------------------------------
        // Use the same JWT again
        // -----------------------------------------------------

        mockMvc.perform(
                        post("/api/auth/logout")
                                .header(
                                        "Authorization",
                                        "Bearer " + token
                                )
                )
                .andExpect(status().isUnauthorized());
    }


    // =========================================================
    // LOGOUT WITHOUT AUTHORIZATION HEADER
    // =========================================================

    @Test
    void logoutWithoutAuthorizationHeaderShouldBeUnauthorized()
            throws Exception {

        mockMvc.perform(
                        post("/api/auth/logout")
                )
                .andExpect(status().isUnauthorized());
    }


    // =========================================================
    // LOGOUT WITH INVALID JWT
    // =========================================================

    @Test
    void logoutWithInvalidJwtShouldBeUnauthorized()
            throws Exception {

        mockMvc.perform(
                        post("/api/auth/logout")
                                .header(
                                        "Authorization",
                                        "Bearer invalid-jwt-token"
                                )
                )
                .andExpect(status().isUnauthorized());
    }
}