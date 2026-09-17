package com.chatApplication.message_service.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;
import org.springframework.web.socket.messaging.SessionUnsubscribeEvent;
import org.springframework.stereotype.Component;

/**
 * Temporary diagnostic listener for subscription registration timing.
 *
 * Captures:
 *   SUBSCRIPTION_REGISTERED — when Spring fires SessionSubscribeEvent
 *   SUBSCRIPTION_UNREGISTERED — when Spring fires SessionUnsubscribeEvent
 *
 * IMPORTANT: This listener intentionally does NOT query the
 * SubscriptionRegistry (findSubscriptions()) during event processing.
 * Doing so would prematurely populate the DefaultSubscriptionRegistry's
 * DestinationCache via computeIfAbsent(), racing with the asynchronous
 * SimpleBrokerMessageHandler subscription registration and causing
 * duplicate subscription IDs in the cache ([0, 0] instead of [0]).
 *
 * PURPOSE: Investigation only. Remove after RCA is complete.
 */
@Component
public class StompSubscriptionEventListener {

    private static final Logger log =
            LoggerFactory.getLogger("STOMP_DIAGNOSTIC");

    @EventListener
    public void onSessionSubscribe(SessionSubscribeEvent event) {
        StompHeaderAccessor accessor =
                StompHeaderAccessor.wrap(event.getMessage());

        long ts = System.currentTimeMillis();
        String sessionId = accessor.getSessionId();
        String destination = accessor.getDestination();
        String subId = accessor.getSubscriptionId();
        String threadName = Thread.currentThread().getName();

        String userId = "unknown";
        if (accessor.getUser() != null) {
            userId = accessor.getUser().getName();
        }

        Long subscribeReceivedTs = StompDiagnosticInterceptor
                .getAndRemoveSubscribeTimestamp(sessionId, subId);
        String deltaStr = "unknown";
        if (subscribeReceivedTs != null) {
            long delta = ts - subscribeReceivedTs;
            deltaStr = String.valueOf(delta);
        }

        log.info("[BROKER] SUBSCRIPTION_REGISTERED ts={} session={} user={} dest={} subId={} " +
                        "registrationDeltaMs={} thread={}",
                ts, sessionId, userId, destination, subId,
                deltaStr, threadName);
    }

    @EventListener
    public void onSessionUnsubscribe(SessionUnsubscribeEvent event) {
        StompHeaderAccessor accessor =
                StompHeaderAccessor.wrap(event.getMessage());

        long ts = System.currentTimeMillis();
        String sessionId = accessor.getSessionId();
        String subId = accessor.getSubscriptionId();
        String threadName = Thread.currentThread().getName();

        log.info("[BROKER] SUBSCRIPTION_UNREGISTERED ts={} session={} subId={} thread={}",
                ts, sessionId, subId, threadName);
    }
}
