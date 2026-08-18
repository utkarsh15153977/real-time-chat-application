package com.chatapp.auth_service.security;

import com.chatapp.auth_service.repository.BlacklistedTokenRepository;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class JwtAuthenticationFilterTest {
    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private BlacklistedTokenRepository blacklistRepository;

    @Mock
    private FilterChain filterChain;

    @InjectMocks
    private JwtAuthenticationFilter jwtAuthenticationFilter;


    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
    }


    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }


    // =========================================================
    // VALID JWT
    // =========================================================

    @Test
    void validJwtShouldAuthenticateUser()
            throws Exception {

        String token = "valid-jwt-token";
        String email = "user@gmail.com";
        Long userId = 100L;

        MockHttpServletRequest request =
                new MockHttpServletRequest();

        MockHttpServletResponse response =
                new MockHttpServletResponse();

        request.addHeader(
                "Authorization",
                "Bearer " + token
        );

        when(blacklistRepository.findByToken(token))
                .thenReturn(Optional.empty());

        when(jwtUtil.validateToken(token))
                .thenReturn(true);

        when(jwtUtil.extractUsername(token))
                .thenReturn(email);

        when(jwtUtil.extractUserId(token))
                .thenReturn(userId);

        jwtAuthenticationFilter.doFilter(
                request,
                response,
                filterChain
        );

        assertNotNull(
                SecurityContextHolder
                        .getContext()
                        .getAuthentication()
        );

        assertEquals(
                email,
                SecurityContextHolder
                        .getContext()
                        .getAuthentication()
                        .getPrincipal()
        );

        assertEquals(
                userId,
                SecurityContextHolder
                        .getContext()
                        .getAuthentication()
                        .getDetails()
        );

        assertTrue(
                SecurityContextHolder
                        .getContext()
                        .getAuthentication()
                        .getAuthorities()
                        .stream()
                        .anyMatch(
                                authority ->
                                        authority
                                                .getAuthority()
                                                .equals("ROLE_USER")
                        )
        );

        verify(jwtUtil).validateToken(token);
        verify(jwtUtil).extractUsername(token);
        verify(jwtUtil).extractUserId(token);

        verify(filterChain).doFilter(
                request,
                response
        );
    }


    // =========================================================
    // BLACKLISTED JWT
    // =========================================================

    @Test
    void blacklistedJwtShouldNotAuthenticateUser()
            throws Exception {

        String token = "blacklisted-jwt-token";

        MockHttpServletRequest request =
                new MockHttpServletRequest();

        MockHttpServletResponse response =
                new MockHttpServletResponse();

        request.addHeader(
                "Authorization",
                "Bearer " + token
        );

        when(blacklistRepository.findByToken(token))
                .thenReturn(
                        Optional.of(
                                new com.chatapp.auth_service.entity.BlacklistedToken()
                        )
                );

        jwtAuthenticationFilter.doFilter(
                request,
                response,
                filterChain
        );

        assertNull(
                SecurityContextHolder
                        .getContext()
                        .getAuthentication()
        );

        verify(blacklistRepository)
                .findByToken(token);

        verify(jwtUtil, never())
                .validateToken(token);

        verify(jwtUtil, never())
                .extractUsername(token);

        verify(jwtUtil, never())
                .extractUserId(token);

        verify(filterChain).doFilter(
                request,
                response
        );
    }


    // =========================================================
    // INVALID JWT
    // =========================================================

    @Test
    void invalidJwtShouldNotAuthenticateUser()
            throws Exception {

        String token = "invalid-jwt-token";

        MockHttpServletRequest request =
                new MockHttpServletRequest();

        MockHttpServletResponse response =
                new MockHttpServletResponse();

        request.addHeader(
                "Authorization",
                "Bearer " + token
        );

        when(blacklistRepository.findByToken(token))
                .thenReturn(Optional.empty());

        when(jwtUtil.validateToken(token))
                .thenReturn(false);

        jwtAuthenticationFilter.doFilter(
                request,
                response,
                filterChain
        );

        assertNull(
                SecurityContextHolder
                        .getContext()
                        .getAuthentication()
        );

        verify(jwtUtil)
                .validateToken(token);

        verify(jwtUtil, never())
                .extractUsername(token);

        verify(jwtUtil, never())
                .extractUserId(token);

        verify(filterChain).doFilter(
                request,
                response
        );
    }


    // =========================================================
    // JWT VALIDATION THROWS EXCEPTION
    // =========================================================

    @Test
    void jwtExceptionShouldNotAuthenticateUser()
            throws Exception {

        String token = "expired-jwt-token";

        MockHttpServletRequest request =
                new MockHttpServletRequest();

        MockHttpServletResponse response =
                new MockHttpServletResponse();

        request.addHeader(
                "Authorization",
                "Bearer " + token
        );

        when(blacklistRepository.findByToken(token))
                .thenReturn(Optional.empty());

        when(jwtUtil.validateToken(token))
                .thenThrow(
                        new RuntimeException(
                                "JWT expired"
                        )
                );

        jwtAuthenticationFilter.doFilter(
                request,
                response,
                filterChain
        );

        assertNull(
                SecurityContextHolder
                        .getContext()
                        .getAuthentication()
        );

        verify(jwtUtil)
                .validateToken(token);

        verify(filterChain).doFilter(
                request,
                response
        );
    }


    // =========================================================
    // NO AUTHORIZATION HEADER
    // =========================================================

    @Test
    void requestWithoutJwtShouldContinue()
            throws Exception {

        MockHttpServletRequest request =
                new MockHttpServletRequest();

        MockHttpServletResponse response =
                new MockHttpServletResponse();

        jwtAuthenticationFilter.doFilter(
                request,
                response,
                filterChain
        );

        assertNull(
                SecurityContextHolder
                        .getContext()
                        .getAuthentication()
        );

        verifyNoInteractions(
                jwtUtil,
                blacklistRepository
        );

        verify(filterChain).doFilter(
                request,
                response
        );
    }


    // =========================================================
    // INVALID AUTHORIZATION HEADER
    // =========================================================

    @Test
    void nonBearerAuthorizationHeaderShouldBeIgnored()
            throws Exception {

        MockHttpServletRequest request =
                new MockHttpServletRequest();

        MockHttpServletResponse response =
                new MockHttpServletResponse();

        request.addHeader(
                "Authorization",
                "Basic some-value"
        );

        jwtAuthenticationFilter.doFilter(
                request,
                response,
                filterChain
        );

        assertNull(
                SecurityContextHolder
                        .getContext()
                        .getAuthentication()
        );

        verifyNoInteractions(
                jwtUtil,
                blacklistRepository
        );

        verify(filterChain).doFilter(
                request,
                response
        );
    }
}
