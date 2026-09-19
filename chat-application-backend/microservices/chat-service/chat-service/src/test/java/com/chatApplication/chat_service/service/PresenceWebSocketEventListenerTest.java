package com.chatApplication.chat_service.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PresenceWebSocketEventListenerTest {

    @Mock
    private PresenceService presenceService;

    @Captor
    private ArgumentCaptor<Long> userIdCaptor;

    private PresenceWebSocketEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new PresenceWebSocketEventListener(presenceService);
    }

    private Principal createPrincipal(String userId) {
        return new UsernamePasswordAuthenticationToken(
                userId, null, Collections.emptyList());
    }

    private SessionConnectEvent createConnectEvent(
            String sessionId,
            Principal principal,
            Map<String, String> nativeHeaders,
            Map<String, Object> sessionAttributes) {

        SimpMessageHeaderAccessor accessor =
                SimpMessageHeaderAccessor.create(SimpMessageType.CONNECT);
        accessor.setSessionId(sessionId);

        if (principal != null) {
            accessor.setUser(principal);
        }

        if (nativeHeaders != null) {
            for (Map.Entry<String, String> entry : nativeHeaders.entrySet()) {
                accessor.addNativeHeader(entry.getKey(), entry.getValue());
            }
        }

        Map<String, Object> attrs = sessionAttributes != null
                ? new HashMap<>(sessionAttributes) : new HashMap<>();
        accessor.setSessionAttributes(attrs);

        Message<byte[]> message = new GenericMessage<>(
                new byte[0], accessor.getMessageHeaders());

        return new SessionConnectEvent(this, message, principal);
    }

    private SessionConnectEvent createConnectEvent(
            String sessionId,
            Principal principal,
            Map<String, String> nativeHeaders) {
        return createConnectEvent(sessionId, principal, nativeHeaders, null);
    }

    private SessionDisconnectEvent createDisconnectEvent(String sessionId) {
        SimpMessageHeaderAccessor accessor =
                SimpMessageHeaderAccessor.create(SimpMessageType.DISCONNECT);
        accessor.setSessionId(sessionId);
        accessor.setSessionAttributes(new HashMap<>());

        Message<byte[]> message = new GenericMessage<>(
                new byte[0], accessor.getMessageHeaders());

        return new SessionDisconnectEvent(
                this, message, sessionId, CloseStatus.NORMAL);
    }

    @Test
    @DisplayName("SECURITY-1: Valid authenticated user registers presence correctly")
    void handleConnect_validAuthenticatedUser_registersPresence() {
        Principal principal = createPrincipal("123");
        SessionConnectEvent event = createConnectEvent("session-1", principal, null);

        listener.handleSessionConnected(event);

        verify(presenceService).setUserOnline(123L);
    }

    @Test
    @DisplayName("SECURITY-2: Identity spoofing - native userId header is IGNORED, Principal wins")
    void handleConnect_identitySpoofing_nativeUserIdHeaderIgnored() {
        Principal attackerPrincipal = createPrincipal("123");
        Map<String, String> nativeHeaders = Map.of("userId", "456");

        SessionConnectEvent event = createConnectEvent(
                "session-2", attackerPrincipal, nativeHeaders);

        listener.handleSessionConnected(event);

        ArgumentCaptor<Long> captor = ArgumentCaptor.forClass(Long.class);
        verify(presenceService).setUserOnline(captor.capture());
        assertThat(captor.getValue()).isEqualTo(123L);
    }

    @Test
    @DisplayName("SECURITY-3: Missing native userId - Principal identity used")
    void handleConnect_missingNativeUserId_principalIdentityUsed() {
        Principal principal = createPrincipal("789");
        SessionConnectEvent event = createConnectEvent("session-3", principal, null);

        listener.handleSessionConnected(event);

        verify(presenceService).setUserOnline(789L);
    }

    @Test
    @DisplayName("SECURITY-4: Missing Principal AND missing session attributes - presence skipped")
    void handleConnect_missingPrincipalAndSessionAttrs_presenceSkipped() {
        Map<String, String> nativeHeaders = Map.of("userId", "456");
        SessionConnectEvent event = createConnectEvent("session-4", null, nativeHeaders);

        listener.handleSessionConnected(event);

        verify(presenceService, never()).setUserOnline(anyLong());
    }

    @Test
    @DisplayName("SECURITY-5: Conflicting identities - Principal always wins")
    void handleConnect_conflictingIdentities_principalWins() {
        Principal principal = createPrincipal("123");
        Map<String, String> nativeHeaders = Map.of(
                "userId", "456",
                "X-User-Id", "789",
                "X-User-Email", "spoofed@example.com");

        SessionConnectEvent event = createConnectEvent(
                "session-5", principal, nativeHeaders);

        listener.handleSessionConnected(event);

        ArgumentCaptor<Long> captor = ArgumentCaptor.forClass(Long.class);
        verify(presenceService).setUserOnline(captor.capture());
        assertThat(captor.getValue()).isEqualTo(123L);
    }

    @Test
    @DisplayName("SECURITY-6: Empty userId header - Principal identity remains")
    void handleConnect_emptyUserIdHeader_principalIdentityRemains() {
        Principal principal = createPrincipal("123");
        Map<String, String> nativeHeaders = Map.of("userId", "");

        SessionConnectEvent event = createConnectEvent(
                "session-6", principal, nativeHeaders);

        listener.handleSessionConnected(event);

        ArgumentCaptor<Long> captor = ArgumentCaptor.forClass(Long.class);
        verify(presenceService).setUserOnline(captor.capture());
        assertThat(captor.getValue()).isEqualTo(123L);
    }

    @Test
    @DisplayName("SECURITY-7: Non-numeric Principal userId - registration skipped")
    void handleConnect_nonNumericPrincipalUserId_registrationSkipped() {
        Principal principal = createPrincipal("not-a-number");
        SessionConnectEvent event = createConnectEvent("session-7", principal, null);

        listener.handleSessionConnected(event);

        verify(presenceService, never()).setUserOnline(anyLong());
    }

    @Test
    @DisplayName("SECURITY-8: Disconnect with valid session removes presence")
    void handleDisconnect_validSession_removesPresence() {
        Principal principal = createPrincipal("123");
        SessionConnectEvent connectEvent = createConnectEvent(
                "session-8", principal, null);
        listener.handleSessionConnected(connectEvent);

        SessionDisconnectEvent disconnectEvent = createDisconnectEvent("session-8");
        listener.handleSessionDisconnect(disconnectEvent);

        verify(presenceService).setUserOnline(123L);
        verify(presenceService).setUserOffline(123L);
    }

    @Test
    @DisplayName("SECURITY-9: Disconnect with unknown session - no-op")
    void handleDisconnect_unknownSession_noOp() {
        SessionDisconnectEvent disconnectEvent = createDisconnectEvent("unknown-session");
        listener.handleSessionDisconnect(disconnectEvent);

        verify(presenceService, never()).setUserOffline(anyLong());
    }

    @Test
    @DisplayName("SECURITY-10: Multiple sessions same user - both use authenticated identity")
    void handleDisconnect_multipleSessionsSameUser_bothUseAuthenticatedIdentity() {
        Principal principal = createPrincipal("123");

        SessionConnectEvent event1 = createConnectEvent("session-A", principal, null);
        SessionConnectEvent event2 = createConnectEvent("session-B", principal, null);
        listener.handleSessionConnected(event1);
        listener.handleSessionConnected(event2);

        verify(presenceService, times(2)).setUserOnline(123L);

        reset(presenceService);

        SessionDisconnectEvent disconnectEvent = createDisconnectEvent("session-A");
        listener.handleSessionDisconnect(disconnectEvent);

        verify(presenceService).setUserOffline(123L);
    }

    @Test
    @DisplayName("SECURITY-11: All spoofing vectors - userId, X-User-Id, X-User-Email, token ignored")
    void handleConnect_allSpoofingVectors_ignored() {
        Principal principal = createPrincipal("100");
        Map<String, String> nativeHeaders = Map.of(
                "userId", "999",
                "X-User-Id", "888",
                "X-User-Email", "attacker@evil.com",
                "token", "fake-token");

        SessionConnectEvent event = createConnectEvent(
                "session-11", principal, nativeHeaders);

        listener.handleSessionConnected(event);

        ArgumentCaptor<Long> captor = ArgumentCaptor.forClass(Long.class);
        verify(presenceService).setUserOnline(captor.capture());
        assertThat(captor.getValue()).isEqualTo(100L);
        assertThat(captor.getValue()).isNotEqualTo(999L);
        assertThat(captor.getValue()).isNotEqualTo(888L);
    }

    @Test
    @DisplayName("SECURITY-12: No Principal but session attributes have user_id - uses session attributes")
    void handleConnect_noPrincipal_sessionAttributesHasUserId_usesSessionAttributes() {
        Map<String, Object> sessionAttributes = new HashMap<>();
        sessionAttributes.put("user_id", "321");
        sessionAttributes.put("token_exp", java.time.Instant.now().plusSeconds(3600));

        Map<String, String> nativeHeaders = Map.of("userId", "999");

        SessionConnectEvent event = createConnectEvent(
                "session-12", null, nativeHeaders, sessionAttributes);

        listener.handleSessionConnected(event);

        ArgumentCaptor<Long> captor = ArgumentCaptor.forClass(Long.class);
        verify(presenceService).setUserOnline(captor.capture());
        assertThat(captor.getValue()).isEqualTo(321L);
    }

    @Test
    @DisplayName("SECURITY-13: No Principal, session attributes empty - native userId NOT used")
    void handleConnect_noPrincipal_sessionAttributesEmpty_nativeUserIdIgnored() {
        Map<String, Object> sessionAttributes = new HashMap<>();

        Map<String, String> nativeHeaders = Map.of("userId", "999");

        SessionConnectEvent event = createConnectEvent(
                "session-13", null, nativeHeaders, sessionAttributes);

        listener.handleSessionConnected(event);

        verify(presenceService, never()).setUserOnline(anyLong());
    }

    @Test
    @DisplayName("SECURITY-14: Principal preferred over session attributes when both available")
    void handleConnect_bothPrincipalAndSessionAttributes_principalWins() {
        Principal principal = createPrincipal("111");
        Map<String, Object> sessionAttributes = new HashMap<>();
        sessionAttributes.put("user_id", "222");

        SessionConnectEvent event = createConnectEvent(
                "session-14", principal, null, sessionAttributes);

        listener.handleSessionConnected(event);

        ArgumentCaptor<Long> captor = ArgumentCaptor.forClass(Long.class);
        verify(presenceService).setUserOnline(captor.capture());
        assertThat(captor.getValue()).isEqualTo(111L);
        assertThat(captor.getValue()).isNotEqualTo(222L);
    }
}