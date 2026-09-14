package com.chatApplication.message_service.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Temporary diagnostic interceptor for WebSocket/STOMP investigation.
 *
 * Captures:
 *   SUBSCRIBE_RECEIVED — when a SUBSCRIBE frame arrives on clientInboundChannel
 *   SEND_RECEIVED — when a SEND frame arrives on clientInboundChannel
 *   MESSAGE_OUTBOUND — when a MESSAGE frame is dispatched on clientOutboundChannel
 *
 * All timestamps are System.currentTimeMillis() for correlation with k6 logs.
 *
 * PURPOSE: Investigation only. Remove after Root Cause Analysis is complete.
 */
public class StompDiagnosticInterceptor implements ChannelInterceptor {

    private static final Logger log =
            LoggerFactory.getLogger("STOMP_DIAGNOSTIC");

    /**
     * Tracks SUBSCRIBE_RECEIVED timestamps for correlation with
     * SUBSCRIPTION_REGISTERED events.
     * Key: "sessionId:subscriptionId", Value: timestamp millis
     *
     * PURPOSE: Investigation only.
     */
    private static final ConcurrentHashMap<String, Long> SUBSCRIBE_TIMESTAMPS =
            new ConcurrentHashMap<>();

    /**
     * Retrieves and removes the SUBSCRIBE_RECEIVED timestamp for a subscription.
     * Returns null if no timestamp was recorded.
     *
     * PURPOSE: Investigation only.
     */
    public static Long getAndRemoveSubscribeTimestamp(
            String sessionId, String subId) {
        if (sessionId == null || subId == null) {
            return null;
        }
        return SUBSCRIBE_TIMESTAMPS.remove(sessionId + ":" + subId);
    }

    private final String channelName;

    public StompDiagnosticInterceptor(String channelName) {
        this.channelName = channelName;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(
                        message, StompHeaderAccessor.class);

        if (accessor == null) {
            return message;
        }

        StompCommand command = accessor.getCommand();
        if (command == null) {
            return message;
        }

        long ts = System.currentTimeMillis();
        String threadName = Thread.currentThread().getName();
        String sessionId = accessor.getSessionId();
        String userId = "unknown";

        // Extract userId from session attributes if available
        if (accessor.getSessionAttributes() != null) {
            Object uid = accessor.getSessionAttributes().get("user_id");
            if (uid != null) {
                userId = uid.toString();
            }
        }

        // Also try to get userId from the USER_HEADER (set by auth interceptor)
        if ("unknown".equals(userId) && accessor.getUser() != null) {
            userId = accessor.getUser().getName();
        }

        switch (command) {
            case SUBSCRIBE -> {
                String destination = accessor.getDestination();
                String subId = accessor.getSubscriptionId();
                String key = sessionId + ":" + subId;
                SUBSCRIBE_TIMESTAMPS.put(key, ts);
                log.info("[{}] SUBSCRIBE_RECEIVED ts={} session={} user={} dest={} subId={} thread={}",
                        channelName, ts, sessionId, userId,
                        destination, subId, threadName);
            }
            case UNSUBSCRIBE -> {
                String subId = accessor.getSubscriptionId();
                log.info("[{}] UNSUBSCRIBE_RECEIVED ts={} session={} user={} subId={} thread={}",
                        channelName, ts, sessionId, userId,
                        subId, threadName);
            }
            case SEND -> {
                String destination = accessor.getDestination();
                // Attempt to extract loadTestId from body for correlation
                String loadTestId = "unknown";
                try {
                    byte[] payload = (byte[]) message.getPayload();
                    String body = new String(payload, java.nio.charset.StandardCharsets.UTF_8);
                    if (body.contains("loadTestId")) {
                        int idx = body.indexOf("\"loadTestId\"");
                        if (idx >= 0) {
                            int colonIdx = body.indexOf(':', idx);
                            int startQuote = body.indexOf('"', colonIdx + 1);
                            int endQuote = body.indexOf('"', startQuote + 1);
                            if (startQuote >= 0 && endQuote >= 0) {
                                loadTestId = body.substring(startQuote + 1, endQuote);
                            }
                        }
                    }
                } catch (Exception ignored) {
                }
                log.info("[{}] SEND_RECEIVED ts={} session={} user={} dest={} loadTestId={} thread={}",
                        channelName, ts, sessionId, userId,
                        destination, loadTestId, threadName);
            }
            case MESSAGE -> {
                String destination = accessor.getDestination();
                String subscriptionId = accessor.getSubscriptionId();
                log.info("[{}] MESSAGE_OUTBOUND ts={} session={} dest={} subId={} thread={}",
                        channelName, ts, sessionId,
                        destination, subscriptionId, threadName);
            }
            case CONNECT -> {
                log.info("[{}] CONNECT_RECEIVED ts={} session={} thread={}",
                        channelName, ts, sessionId, threadName);
            }
            case CONNECTED -> {
                log.info("[{}] CONNECTED_OUTBOUND ts={} session={} thread={}",
                        channelName, ts, sessionId, threadName);
            }
            case DISCONNECT -> {
                log.info("[{}] DISCONNECT_RECEIVED ts={} session={} thread={}",
                        channelName, ts, sessionId, threadName);
            }
            case ERROR -> {
                log.info("[{}] ERROR_OUTBOUND ts={} session={} thread={}",
                        channelName, ts, sessionId, threadName);
            }
            default -> {
                // Ack, Nack, Receipt — log at debug level
                log.debug("[{}] {} ts={} session={} thread={}",
                        channelName, command, ts, sessionId, threadName);
            }
        }

        return message;
    }
}
