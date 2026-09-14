package com.chatApplication.message_service.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.broker.AbstractBrokerMessageHandler;
import org.springframework.messaging.simp.broker.SimpleBrokerMessageHandler;
import org.springframework.messaging.simp.broker.SubscriptionRegistry;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.util.MultiValueMap;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;
import org.springframework.web.socket.messaging.SessionUnsubscribeEvent;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Temporary diagnostic listener for subscription registration timing.
 *
 * Captures:
 *   SUBSCRIPTION_REGISTERED — when Spring fires SessionSubscribeEvent
 *   SUBSCRIPTION_UNREGISTERED — when Spring fires SessionUnsubscribeEvent
 *   REGISTRY_SNAPSHOT — subscription state at registration time
 *
 * This event fires AFTER DefaultSubscriptionRegistry.addSubscription()
 * has completed. The timestamp represents the point at which the
 * subscription is AVAILABLE for message routing.
 *
 * PURPOSE: Investigation only. Remove after RCA is complete.
 */
@Component
public class StompSubscriptionEventListener {

    private static final Logger log =
            LoggerFactory.getLogger("STOMP_DIAGNOSTIC");

    private final SubscriptionRegistry subscriptionRegistry;

    public StompSubscriptionEventListener(
            List<AbstractBrokerMessageHandler> brokerHandlers) {
        this.subscriptionRegistry = brokerHandlers.stream()
                .filter(h -> h instanceof SimpleBrokerMessageHandler)
                .map(h -> ((SimpleBrokerMessageHandler) h)
                        .getSubscriptionRegistry())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "No SimpleBrokerMessageHandler found"));
    }

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

        try {
            SimpMessageHeaderAccessor snapshotAccessor =
                    SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
            snapshotAccessor.setDestination(destination);
            Message<?> probe = MessageBuilder.createMessage(
                    "", snapshotAccessor.getMessageHeaders());
            MultiValueMap<String, String> allMatches =
                    subscriptionRegistry.findSubscriptions(probe);

            int totalSessions = allMatches.size();
            int totalSubscriptions = 0;
            StringBuilder detail = new StringBuilder();
            for (var entry : allMatches.entrySet()) {
                totalSubscriptions += entry.getValue().size();
                if (detail.length() > 0) {
                    detail.append(",");
                }
                detail.append(entry.getKey())
                        .append(":")
                        .append(entry.getValue());
            }

            log.info("[BROKER] REGISTRY_SNAPSHOT ts={} dest={} " +
                            "totalSessions={} totalSubscriptions={} " +
                            "sessionSubMap=[{}] thread={}",
                    ts, destination, totalSessions,
                    totalSubscriptions, detail, threadName);
        } catch (Exception e) {
            log.warn("[BROKER] REGISTRY_SNAPSHOT_FAILED ts={} dest={} error={}",
                    ts, destination, e.getMessage());
        }
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
