package com.chatapp.auth_service.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.chatapp.auth_service.AuthServiceApplication;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 * KAN-13 Security Tests: JWT Secret Consistency
 *
 * Tests that verify the JWT secret configuration is consistent across
 * issuer (auth-service) and validators (api-gateway, message-service, etc.).
 */
class JwtSecretConsistencyTest {

    private static final String SHARED_SECRET = "testSharedSecretKeyForKan13ConsistencyCheck2026!!";

    private SecretKey buildKey(String secret) {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    private String generateToken(String secret, String userId, String email) {
        SecretKey key = buildKey(secret);
        Date now = new Date();
        return Jwts.builder()
                .subject(email)
                .claim("userId", userId)
                .claim("name", "Test User")
                .id("jti-" + userId)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + 3600000))
                .signWith(key)
                .compact();
    }

    @Test
    @DisplayName("TEST 1: Shared secret - token signed with issuer key validates with gateway key")
    void sharedSecret_tokenValidatesAcrossServices() {
        String issuerSecret = SHARED_SECRET;
        String validatorSecret = SHARED_SECRET;

        String token = generateToken(issuerSecret, "user-1", "user@example.com");

        SecretKey validatorKey = buildKey(validatorSecret);
        Claims claims = Jwts.parser()
                .verifyWith(validatorKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        assertEquals("user@example.com", claims.getSubject());
        assertEquals("user-1", claims.get("userId", String.class));
    }

    @Test
    @DisplayName("TEST 3: Different secrets - token signed with one key fails validation with different key")
    void differentSecrets_tokenValidationFails() {
        String issuerSecret = "secretKeyUsedByAuthServiceForSigning2026!!";
        String validatorSecret = "differentSecretKeyUsedByGatewayForValidation!!";

        String token = generateToken(issuerSecret, "user-1", "user@example.com");

        SecretKey validatorKey = buildKey(validatorSecret);
        assertThrows(Exception.class, () -> {
            Jwts.parser()
                    .verifyWith(validatorKey)
                    .build()
                    .parseSignedClaims(token);
        });
    }

    @Test
    @DisplayName("TEST 4: Invalid token - rejected by validator")
    void invalidToken_rejected() {
        String invalidToken = "eyJhbGciOiJIUzI1NiJ9.eyJ1c2VySWQiOiIxMjMifQ.invalidsignature";

        SecretKey validatorKey = buildKey(SHARED_SECRET);
        assertThrows(Exception.class, () -> {
            Jwts.parser()
                    .verifyWith(validatorKey)
                    .build()
                    .parseSignedClaims(invalidToken);
        });
    }

    @Test
    @DisplayName("TEST 4b: Completely unrelated token - rejected")
    void unrelatedToken_rejected() {
        String unrelatedSecret = "completelyUnrelatedSecretKeyForOtherSystem2026!!";
        String token = generateToken(unrelatedSecret, "hacker", "hacker@evil.com");

        SecretKey validatorKey = buildKey(SHARED_SECRET);
        assertThrows(Exception.class, () -> {
            Jwts.parser()
                    .verifyWith(validatorKey)
                    .build()
                    .parseSignedClaims(token);
        });
    }

    @Test
    @DisplayName("TEST 6: No hardcoded fallback in auth-service - uses ${JWT_SECRET}")
    void authService_usesEnvVar() throws Exception {
        java.util.Properties props = new java.util.Properties();
        java.io.InputStream is = getClass().getClassLoader().getResourceAsStream("application.properties");
        assertNotNull(is, "application.properties must exist on classpath");
        props.load(is);
        String jwtSecret = props.getProperty("jwt.secret");
        assertNotNull(jwtSecret, "jwt.secret must be defined in application.properties");
        assertTrue(jwtSecret.contains("${JWT_SECRET}"),
                "auth-service jwt.secret must use ${JWT_SECRET} env var, not hardcoded value");
        assertFalse(jwtSecret.contains("chatAppDev"),
                "auth-service must not contain hardcoded fallback secret");
    }

    @Test
    @DisplayName("TEST 6e: Shared secret signs and validates the same JWT claims consistently")
    void sharedSecret_signAndValidateConsistent() {
        String token = generateToken(SHARED_SECRET, "42", "alice@example.com");

        SecretKey key = buildKey(SHARED_SECRET);
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        assertEquals("alice@example.com", claims.getSubject());
        assertEquals("42", claims.get("userId", String.class));
        assertEquals("Test User", claims.get("name", String.class));
        assertNotNull(claims.getId());
        assertNotNull(claims.getIssuedAt());
        assertNotNull(claims.getExpiration());
    }

    @Test
    @DisplayName("TEST 8: Existing auth regression - valid tokens accepted, expired tokens rejected")
    void existingAuthRegression_validTokensAccepted_expiredRejected() {
        String validToken = generateToken(SHARED_SECRET, "user-1", "user@example.com");
        SecretKey key = buildKey(SHARED_SECRET);

        // Valid token should parse successfully
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(validToken)
                .getPayload();
        assertEquals("user@example.com", claims.getSubject());

        // Expired token should be rejected
        Date past = new Date(System.currentTimeMillis() - 7200000);
        Date pastExpiry = new Date(System.currentTimeMillis() - 3600000);
        String expiredToken = Jwts.builder()
                .subject("user@example.com")
                .claim("userId", "user-1")
                .issuedAt(past)
                .expiration(pastExpiry)
                .signWith(key)
                .compact();

        assertThrows(Exception.class, () -> {
            Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(expiredToken);
        });
    }

    @Test
    @DisplayName("TEST 2: Missing JWT_SECRET - context fails to start (fail-fast)")
    void missingJwtSecret_contextFailsToStart() {
        final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
                .withConfiguration(org.springframework.boot.autoconfigure.AutoConfigurations
                        .of(AuthServiceApplication.class))
                .withPropertyValues("spring.main.allow-bean-definition-overriding=true");

        contextRunner.run(context -> {
            assertThat(context).hasFailed();
            Throwable failure = context.getStartupFailure();
            assertThat(failure).isNotNull();
            String msg = failure.getMessage();
            assertThat(msg).contains("jwtUtil");
            assertThat(msg).contains("Injection of autowired dependencies failed");
        });
    }

    @Test
    @DisplayName("TEST 2b: Missing JWT_SECRET - failure does not expose any secret value")
    void missingJwtSecret_failureDoesNotExposeSecret() {
        final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
                .withConfiguration(org.springframework.boot.autoconfigure.AutoConfigurations
                        .of(AuthServiceApplication.class))
                .withPropertyValues("spring.main.allow-bean-definition-overriding=true");

        contextRunner.run(context -> {
            assertThat(context).hasFailed();
            Throwable failure = context.getStartupFailure();
            assertThat(failure).isNotNull();
            String failureMessage = failure.getMessage();
            assertThat(failureMessage).doesNotContain("chatAppDev");
            assertThat(failureMessage).doesNotContain("blinkChat");
            assertThat(failureMessage).doesNotContain("defaultSecretKey");
        });
    }
}
