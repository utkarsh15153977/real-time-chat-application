package com.chatApplication.message_service.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Unit tests for InternalSecurityFilter.
 * <p>
 * Tests:
 * - Valid X-Internal-Secret header -> request proceeds
 * - Missing X-Internal-Secret header -> 403 Forbidden
 * - Invalid X-Internal-Secret header -> 403 Forbidden
 * - Exempt paths -> bypass validation
 */
@ExtendWith(MockitoExtension.class)
class InternalSecurityFilterTest {

    private InternalSecurityFilter filter;
    private FilterChain filterChain;
    private static final String VALID_SECRET = "blinkInternalSecret2024";

    @BeforeEach
    void setUp() {
        filter = new InternalSecurityFilter();
        ReflectionTestUtils.setField(filter, "expectedSecret", VALID_SECRET);
        filterChain = mock(FilterChain.class);
    }

    @Test
    @DisplayName("Valid X-Internal-Secret header allows request")
    void doFilterInternal_validSecret_allowsRequest() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/messages/123");
        request.addHeader("X-Internal-Secret", VALID_SECRET);

        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("Missing X-Internal-Secret header returns 403")
    void doFilterInternal_missingSecret_returns403() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/messages/123");

        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain, never()).doFilter(any(), any());
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("Forbidden");
        assertThat(response.getContentAsString()).contains("missing internal service secret");
    }

    @Test
    @DisplayName("Invalid X-Internal-Secret header returns 403")
    void doFilterInternal_invalidSecret_returns403() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/messages/123");
        request.addHeader("X-Internal-Secret", "wrong-secret");

        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain, never()).doFilter(any(), any());
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("invalid internal service secret");
    }

    @Test
    @DisplayName("Exempt path /actuator/** bypasses validation")
    void doFilterInternal_actuatorPath_bypassesValidation() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/actuator/health");

        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("Exempt path /test/** bypasses validation")
    void doFilterInternal_testPath_bypassesValidation() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/test/online/user123");

        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("Blank X-Internal-Secret header returns 403")
    void doFilterInternal_blankSecret_returns403() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/messages/123");
        request.addHeader("X-Internal-Secret", "   ");

        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain, never()).doFilter(any(), any());
        assertThat(response.getStatus()).isEqualTo(403);
    }

    @Test
    @DisplayName("Response body is valid JSON")
    void doFilterInternal_rejection_returnsJson() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/messages/123");

        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        assertThat(response.getContentType()).contains("application/json");
        assertThat(response.getContentAsString()).contains("\"status\":403");
        assertThat(response.getContentAsString()).contains("\"error\":\"Forbidden\"");
    }
}
