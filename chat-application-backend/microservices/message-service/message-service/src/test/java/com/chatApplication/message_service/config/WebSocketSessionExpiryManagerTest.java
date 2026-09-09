package com.chatApplication.message_service.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for WebSocketSessionExpiryManager.
 *
 * Tests:
 * - Register and retrieve session expiry
 * - Check expired sessions
 * - Update activity timestamps
 * - Remove sessions
 * - Frame processing permission checks
 * - Cleanup of expired sessions
 * - Multiple session tracking
 */
class WebSocketSessionExpiryManagerTest {

    private WebSocketSessionExpiryManager expiryManager;

    @BeforeEach
    void setUp() {
        expiryManager = new WebSocketSessionExpiryManager();
    }

    @Test
    @DisplayName("Register session stores expiry timestamp")
    void registerSession_storesExpiryTimestamp() {
        Instant expiry = Instant.now().plus(1, ChronoUnit.HOURS);

        expiryManager.registerSession("session-1", expiry);

        assertThat(expiryManager.isSessionExpired("session-1"))
                .isFalse();

        assertThat(expiryManager.getActiveSessionCount())
                .isEqualTo(1);
    }

    @Test
    @DisplayName("Expired session is detected correctly")
    void isSessionExpired_expiredSessionDetected() {
        Instant pastExpiry =
                Instant.now().minus(1, ChronoUnit.HOURS);

        expiryManager.registerSession(
                "session-2",
                pastExpiry
        );

        assertThat(expiryManager.isSessionExpired("session-2"))
                .isTrue();
    }

    @Test
    @DisplayName("Unknown session is considered expired")
    void isSessionExpired_unknownSessionIsExpired() {

        assertThat(
                expiryManager.isSessionExpired("unknown")
        ).isTrue();
    }

    @Test
    @DisplayName("Remove session cleans up tracking")
    void removeSession_cleansUpTracking() {

        Instant expiry =
                Instant.now().plus(1, ChronoUnit.HOURS);

        expiryManager.registerSession(
                "session-3",
                expiry
        );

        assertThat(expiryManager.getActiveSessionCount())
                .isEqualTo(1);

        expiryManager.removeSession("session-3");

        assertThat(expiryManager.getActiveSessionCount())
                .isEqualTo(0);

        assertThat(
                expiryManager.isSessionExpired("session-3")
        ).isTrue();
    }

    @Test
    @DisplayName("canProcessFrame allows non-expired sessions")
    void canProcessFrame_allowsNonExpiredSessions() {

        Instant expiry =
                Instant.now().plus(1, ChronoUnit.HOURS);

        expiryManager.registerSession(
                "session-4",
                expiry
        );

        assertThat(
                expiryManager.canProcessFrame("session-4")
        ).isTrue();
    }

    @Test
    @DisplayName("canProcessFrame rejects expired sessions")
    void canProcessFrame_rejectsExpiredSessions() {

        Instant pastExpiry =
                Instant.now().minus(1, ChronoUnit.HOURS);

        expiryManager.registerSession(
                "session-5",
                pastExpiry
        );

        assertThat(
                expiryManager.canProcessFrame("session-5")
        ).isFalse();
    }

    @Test
    @DisplayName("checkExpiredSessions removes expired sessions")
    void checkExpiredSessions_removesExpiredSessions() {

        Instant pastExpiry =
                Instant.now().minus(1, ChronoUnit.HOURS);

        Instant futureExpiry =
                Instant.now().plus(1, ChronoUnit.HOURS);

        expiryManager.registerSession(
                "expired-1",
                pastExpiry
        );

        expiryManager.registerSession(
                "active-1",
                futureExpiry
        );

        assertThat(expiryManager.getActiveSessionCount())
                .isEqualTo(2);

        expiryManager.checkExpiredSessions();

        assertThat(expiryManager.getActiveSessionCount())
                .isEqualTo(1);

        assertThat(
                expiryManager.isSessionExpired("expired-1")
        ).isTrue();

        assertThat(
                expiryManager.isSessionExpired("active-1")
        ).isFalse();
    }

    @Test
    @DisplayName("updateActivity keeps registered session active")
    void updateActivity_updatesLastActivity() {

        Instant expiry =
                Instant.now().plus(1, ChronoUnit.HOURS);

        expiryManager.registerSession(
                "session-6",
                expiry
        );

        expiryManager.updateActivity("session-6");

        assertThat(expiryManager.getActiveSessionCount())
                .isEqualTo(1);

        assertThat(
                expiryManager.isSessionExpired("session-6")
        ).isFalse();
    }

    @Test
    @DisplayName("updateActivity does nothing for unknown session")
    void updateActivity_unknownSessionDoesNothing() {

        expiryManager.updateActivity("unknown");

        assertThat(expiryManager.getActiveSessionCount())
                .isEqualTo(0);
    }

    @Test
    @DisplayName("Multiple sessions are tracked independently")
    void multipleSessions_trackedIndependently() {

        Instant expiry1 =
                Instant.now().plus(1, ChronoUnit.HOURS);

        Instant expiry2 =
                Instant.now().plus(2, ChronoUnit.HOURS);

        expiryManager.registerSession(
                "session-a",
                expiry1
        );

        expiryManager.registerSession(
                "session-b",
                expiry2
        );

        assertThat(expiryManager.getActiveSessionCount())
                .isEqualTo(2);

        expiryManager.removeSession("session-a");

        assertThat(expiryManager.getActiveSessionCount())
                .isEqualTo(1);

        assertThat(
                expiryManager.canProcessFrame("session-a")
        ).isFalse();

        assertThat(
                expiryManager.canProcessFrame("session-b")
        ).isTrue();
    }

    @Test
    @DisplayName("Null session ID is handled safely")
    void nullSessionId_isHandledSafely() {

        expiryManager.registerSession(
                null,
                Instant.now().plus(1, ChronoUnit.HOURS)
        );

        assertThat(expiryManager.getActiveSessionCount())
                .isEqualTo(0);

        assertThat(
                expiryManager.isSessionExpired(null)
        ).isTrue();

        expiryManager.updateActivity(null);
        expiryManager.removeSession(null);
    }

    @Test
    @DisplayName("Blank session ID is handled safely")
    void blankSessionId_isHandledSafely() {

        expiryManager.registerSession(
                " ",
                Instant.now().plus(1, ChronoUnit.HOURS)
        );

        assertThat(expiryManager.getActiveSessionCount())
                .isEqualTo(0);

        assertThat(
                expiryManager.isSessionExpired(" ")
        ).isTrue();

        expiryManager.updateActivity(" ");
        expiryManager.removeSession(" ");
    }

    @Test
    @DisplayName("Null expiry is rejected safely")
    void nullExpiry_isRejectedSafely() {

        expiryManager.registerSession(
                "session-null-expiry",
                null
        );

        assertThat(expiryManager.getActiveSessionCount())
                .isEqualTo(0);

        assertThat(
                expiryManager.isSessionExpired(
                        "session-null-expiry"
                )
        ).isTrue();
    }
}