package com.chatApplication.message_service.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.broker.SubscriptionRegistry;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.util.MultiValueMap;

/**
 * Diagnostic interceptor for the brokerChannel.
 *
 * Intercepts messages published via SimpMessagingTemplate.convertAndSend()
 * BEFORE they reach SimpleBrokerMessageHandler.sendMessageToSubscribers().
 *
 * Captures:
 *   PUBLISH_TO_BROKER — when a MESSAGE is published to the brokerChannel
 *     with: destination, loadTestId, matched subscription count,
 *     session/subscription IDs, full subscription state.
 *
 *   WARNING_NO_SUBSCRIBERS — when /topic/public publish finds 0 subscriptions.
 *
 * This provides direct evidence of whether subscriptions are available
 * at the moment of publish.
 *
 * PURPOSE: Investigation only. Remove after RCA is complete.
 */
public class BrokerChannelDiagnosticInterceptor implements ChannelInterceptor {

    private static final Logger log =
            LoggerFactory.getLogger("STOMP_DIAGNOSTIC");

    private final SubscriptionRegistry subscriptionRegistry;

    public BrokerChannelDiagnosticInterceptor(
            SubscriptionRegistry subscriptionRegistry) {
        this.subscriptionRegistry = subscriptionRegistry;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        SimpMessageHeaderAccessor accessor =
                SimpMessageHeaderAccessor.wrap(message);

        SimpMessageType messageType = accessor.getMessageType();

        if (SimpMessageType.MESSAGE.equals(messageType)) {
            long ts = System.currentTimeMillis();
            String destination = accessor.getDestination();
            String sessionId = accessor.getSessionId();
            String threadName = Thread.currentThread().getName();

            String loadTestId = extractLoadTestId(message);

            MultiValueMap<String, String> matchedSubs =
                    subscriptionRegistry.findSubscriptions(message);

            int matchCount = matchedSubs.size();
            int totalSubscriptions = 0;
            StringBuilder sessionSubDetail = new StringBuilder();
            for (var entry : matchedSubs.entrySet()) {
                totalSubscriptions += entry.getValue().size();
                if (sessionSubDetail.length() > 0) {
                    sessionSubDetail.append(",");
                }
                sessionSubDetail.append(entry.getKey())
                        .append(":")
                        .append(entry.getValue());
            }

            if (totalSubscriptions == 0
                    && "/topic/public".equals(destination)) {
                log.warn("[BROKER] PUBLISH_TO_BROKER ts={} dest={} " +
                                "loadTestId={} publishSession={} " +
                                "matchedSessions={} totalSubscriptions=0 " +
                                "sessionSubMap=[{}] " +
                                "WARNING_NO_SUBSCRIBERS thread={}",
                        ts, destination, loadTestId, sessionId,
                        matchCount, sessionSubDetail, threadName);
            } else {
                log.info("[BROKER] PUBLISH_TO_BROKER ts={} dest={} " +
                                "loadTestId={} publishSession={} " +
                                "matchedSessions={} totalSubscriptions={} " +
                                "sessionSubMap=[{}] thread={}",
                        ts, destination, loadTestId, sessionId,
                        matchCount, totalSubscriptions,
                        sessionSubDetail, threadName);
            }
        }

        return message;
    }

    private String extractLoadTestId(Message<?> message) {
        try {
            byte[] payload = (byte[]) message.getPayload();
            String body = new String(payload,
                    java.nio.charset.StandardCharsets.UTF_8);
            if (body.contains("loadTestId")) {
                int idx = body.indexOf("\"loadTestId\"");
                if (idx >= 0) {
                    int colonIdx = body.indexOf(':', idx);
                    int startQuote = body.indexOf('"', colonIdx + 1);
                    int endQuote = body.indexOf('"', startQuote + 1);
                    if (startQuote >= 0 && endQuote >= 0) {
                        return body.substring(startQuote + 1, endQuote);
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return "unknown";
    }
}
