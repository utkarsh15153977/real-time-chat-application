package com.chatapp.auth_service.security;

import com.chatapp.auth_service.config.TestConfig;
import com.chatapp.auth_service.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestConfig.class)
public class JwtUtilTest {

    @Autowired
    private JwtUtil jwtUtil;

    private User testUser;
    private String testToken;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id(1L)
                .email("test@example.com")
                .name("Test User")
                .password("encodedPassword")
                .phone("+1234567890")
                .build();

        testToken = jwtUtil.generateToken(testUser);
    }

    @Test
    void testGenerateToken() {
        String token = jwtUtil.generateToken(testUser);
        assertNotNull(token);
        assertTrue(token.split("\\.").length == 3);
        System.out.println("Generated Token: " + token);
    }

    @Test
    void testExtractUsername() {
        String username = jwtUtil.extractUsername(testToken);
        assertNotNull(username);
        assertEquals("test@example.com", username);
    }

    @Test
    void testExtractUserId() {
        Long userId = jwtUtil.extractUserId(testToken);
        assertNotNull(userId);
        assertEquals(1L, userId);
    }

    @Test
    void testValidateToken_ValidToken() {
        boolean isValid = jwtUtil.validateToken(testToken);
        assertTrue(isValid);
    }

    @Test
    void testValidateToken_InvalidToken() {
        String invalidToken = "invalid.token.string";
        boolean isValid = jwtUtil.validateToken(invalidToken);
        assertFalse(isValid);
    }

    @Test
    void testValidateToken_MalformedToken() {
        String malformedToken = "eyJhbGciOiJIUzI1NiJ9";
        boolean isValid = jwtUtil.validateToken(malformedToken);
        assertFalse(isValid);
    }

    @Test
    void testGetExpiration() {
        Date expiration = jwtUtil.getExpiration(testToken);
        assertNotNull(expiration);
        assertTrue(expiration.after(new Date()));
    }

    @Test
    void testTokenContainsAllClaims() {
        Claims claims = Jwts.parser()
                .verifyWith(jwtUtil.getSigningKey())
                .build()
                .parseSignedClaims(testToken)
                .getPayload();

        assertEquals("test@example.com", claims.getSubject());
        assertEquals(1L, claims.get("userId", Long.class));
        assertEquals("Test User", claims.get("name", String.class));
        assertNotNull(claims.getIssuedAt());
        assertNotNull(claims.getExpiration());
    }

    @Test
    void testTokenExpirationTime() {
        Date expiration = jwtUtil.getExpiration(testToken);
        Date now = new Date();
        long diffInMillis = expiration.getTime() - now.getTime();
        long diffInHours = diffInMillis / (1000 * 60 * 60);

        assertTrue(diffInHours >= 23 && diffInHours <= 24);
    }
}