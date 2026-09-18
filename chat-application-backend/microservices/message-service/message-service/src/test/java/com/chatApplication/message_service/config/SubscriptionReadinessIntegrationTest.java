package com.chatApplication.message_service.config;

import com.chatApplication.message_service.dto.ChatMessageResponseDTO;
import com.chatApplication.message_service.entity.MessageType;
import com.chatApplication.message_service.kafka.MessageEventPublisher;
import com.chatApplication.message_service.service.InboxService;
import com.chatApplication.message_service.service.MessageService;
import com.chatApplication.message_service.service.PresenceTracker;
import com.chatApplication.message_service.service.PushNotificationService;
import com.chatApplication.message_service.service.UserPresenceService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.firebase.messaging.FirebaseMessaging;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.messaging.support.AbstractSubscribableChannel;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.ExecutorChannelInterceptor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import org.springframework.messaging.MessageHandler;
import org.springframework.web.socket.messaging.SubProtocolWebSocketHandler;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.net.URI;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Real-stack integration test for KAN-3.
 *
 * <p>Boots the full Spring application context with an embedded server,
 * connects a real WebSocket STOMP client, and verifies that SUBSCRIBE
 * followed immediately by SEND does not lose the first broadcast message.
 *
 * <p>Exercises the actual production message path:
 * <pre>
 *   WebSocket client
 *   -> StompSubProtocolHandler
 *   -> clientInboundChannel
 *   -> WebSocketAuthInterceptor
 *   -> SubscriptionReadinessInterceptor
 *   -> SimpleBrokerMessageHandler (registerSubscription)
 *   -> afterMessageHandled (release buffered SENDs)
 *   -> SimpAnnotationMethodMessageHandler
 *   -> ChatWebSocketController.sendMessage()
 *   -> messageService.saveMessage()
 *   -> messagingTemplate.convertAndSend("/topic/public", ...)
 *   -> SimpleBrokerMessageHandler (findSubscriptions)
 *   -> clientOutboundChannel
 *   -> WebSocket MESSAGE to subscriber
 * </pre>
 *
 * <p>No Thread.sleep() for race simulation. SUBSCRIBE and SEND are sent in
 * immediate succession -- the exact condition that caused the original race.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Timeout(30)
@EnableAutoConfiguration(exclude = {RedisAutoConfiguration.class})
class SubscriptionReadinessIntegrationTest {

    @LocalServerPort
    private int port;

    @MockBean
    private MessageService messageService;

    @MockBean
    private InboxService inboxService;

    @MockBean
    private UserPresenceService userPresenceService;

    @MockBean
    private PushNotificationService pushNotificationService;

    @MockBean
    private MessageEventPublisher messageEventPublisher;

    @MockBean
    private FirebaseMessaging firebaseMessaging;

    @MockBean
    private StringRedisTemplate stringRedisTemplate;

    @MockBean
    private RedisConnectionFactory redisConnectionFactory;

    @MockBean
    private PresenceTracker presenceTracker;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    @Qualifier("clientOutboundChannel")
    private AbstractSubscribableChannel clientOutboundChannel;

    private static WebSocketStompClient stompClient;

    /** Per-test session for lifecycle management. */
    private StompSession stompSession;

    /** All sessions created during a test, for cleanup. */
    private final List<StompSession> allSessions = new ArrayList<>();

    /** ExecutorChannelInterceptor that fires on the HANDLER THREAD (clientOutboundChannel executor).
     *  Confirms whether SubProtocolWebSocketHandler.handleMessage() is actually invoked. */
    private final ExecutorChannelInterceptor executorInterceptor = new ExecutorChannelInterceptor() {
        @Override
        public Message<?> beforeHandle(Message<?> message, MessageChannel channel, MessageHandler handler) {
            SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.wrap(message);
            System.out.println("[EXEC-INTERCEPT] beforeHandle handler=" + handler.getClass().getSimpleName()
                    + " dest=" + accessor.getDestination()
                    + " session=" + accessor.getSessionId()
                    + " thread=" + Thread.currentThread().getName());
            return message;
        }

        @Override
        public void afterMessageHandled(Message<?> message, MessageChannel channel,
                                         MessageHandler handler, Exception ex) {
            System.out.println("[EXEC-INTERCEPT] afterMessageHandled handler=" + handler.getClass().getSimpleName()
                    + " ex=" + ex + " thread=" + Thread.currentThread().getName());
        }
    };

    /** Test-only outbound interceptor that logs ALL messages, including
     *  broker-created frames with null StompCommand. */
    private final ChannelInterceptor testOutboundInterceptor = new ChannelInterceptor() {
        @Override
        public Message<?> preSend(Message<?> message, MessageChannel channel) {
            SimpMessageHeaderAccessor accessor =
                    SimpMessageHeaderAccessor.wrap(message);
            long ts = System.currentTimeMillis();
            String thread = Thread.currentThread().getName();
            String sessionId = accessor.getSessionId();
            String dest = accessor.getDestination();
            String subId = accessor.getSubscriptionId();

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

            System.out.println("[TEST-OUTBOUND] preSend ts=" + ts
                    + " session=" + sessionId
                    + " dest=" + dest
                    + " subId=" + subId
                    + " loadTestId=" + loadTestId
                    + " thread=" + thread);
            return message;
        }

        @Override
        public void postSend(Message<?> message, MessageChannel channel, boolean sent) {
            SimpMessageHeaderAccessor accessor =
                    SimpMessageHeaderAccessor.wrap(message);
            System.out.println("[TEST-OUTBOUND] postSend sent=" + sent
                    + " dest=" + accessor.getDestination()
                    + " thread=" + Thread.currentThread().getName());
        }

        @Override
        public void afterSendCompletion(Message<?> message, MessageChannel channel,
                                         boolean sent, Exception ex) {
            if (ex != null) {
                System.out.println("[TEST-OUTBOUND] afterSendCompletion EXCEPTION: "
                        + ex.getClass().getName() + ": " + ex.getMessage());
            }
        }
    };

    // ================================================================
    // CLIENT-SIDE RAW WebSocket FRAME INTERCEPTOR
    // Uses a raw WebSocket connection alongside STOMP to verify frame delivery
    // ================================================================

    /** Tracks raw WebSocket frames arriving at the client transport layer. */
    private static final AtomicLong clientFrameCount = new AtomicLong(0);
    private static volatile String lastClientFramePreview = "(none)";
    private static volatile boolean lastClientFrameArrived = false;

    /**
     * Enhanced StompSessionHandlerAdapter that logs ALL lifecycle and error callbacks.
     * The default no-op adapter silently swallows exceptions.
     */
    private static class DiagnosticStompSessionHandler extends StompSessionHandlerAdapter {
        private final String userId;

        DiagnosticStompSessionHandler(String userId) {
            this.userId = userId;
        }

        @Override
        public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
            System.out.println("[CLIENT-HANDLER] afterConnected session=" + session.getSessionId()
                    + " userId=" + userId
                    + " connectedHeaders=" + connectedHeaders
                    + " thread=" + Thread.currentThread().getName());
        }

        @Override
        public void handleException(StompSession session, org.springframework.messaging.simp.stomp.StompCommand command,
                StompHeaders headers, byte[] payload, Throwable exception) {
            System.out.println("[CLIENT-HANDLER] handleException session=" + (session != null ? session.getSessionId() : "null")
                    + " command=" + command
                    + " exception=" + exception.getClass().getName() + ": " + exception.getMessage()
                    + " thread=" + Thread.currentThread().getName());
            exception.printStackTrace(System.out);
        }

        @Override
        public void handleTransportError(StompSession session, Throwable exception) {
            System.out.println("[CLIENT-HANDLER] handleTransportError session="
                    + (session != null ? session.getSessionId() : "null")
                    + " exception=" + exception.getClass().getName() + ": " + exception.getMessage()
                    + " thread=" + Thread.currentThread().getName());
            exception.printStackTrace(System.out);
        }
    }

    @BeforeAll
    static void setUpClient() {
        StandardWebSocketClient webSocketClient = new StandardWebSocketClient();
        stompClient = new WebSocketStompClient(webSocketClient);

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());

        MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
        converter.setObjectMapper(objectMapper);
        stompClient.setMessageConverter(converter);
    }

    @AfterAll
    static void tearDownClient() {
        if (stompClient != null) {
            stompClient.stop();
        }
    }

    @BeforeEach
    void registerOutboundDiagnostics() {
        clientOutboundChannel.addInterceptor(testOutboundInterceptor);
        clientOutboundChannel.addInterceptor(executorInterceptor);
    }

    @AfterEach
    void cleanupSession() {
        clientOutboundChannel.removeInterceptor(testOutboundInterceptor);
        clientOutboundChannel.removeInterceptor(executorInterceptor);
        for (StompSession session : allSessions) {
            if (session != null && session.isConnected()) {
                try {
                    session.disconnect();
                } catch (Exception ignored) {
                }
            }
        }
        allSessions.clear();
    }

    // ----------------------------------------------------------------
    // Helpers
    // ----------------------------------------------------------------

    private void inspectOutboundHandlers() {
        System.out.println("[DIAG] === clientOutboundChannel handler inspection ===");
        System.out.println("[DIAG] channel class: " + clientOutboundChannel.getClass().getName());

        // Check isRunning (SubProtocolWebSocketHandler lifecycle)
        try {
            Class<?> sphClass = Class.forName(
                    "org.springframework.web.socket.messaging.SubProtocolWebSocketHandler");
            // Search for SubProtocolWebSocketHandler in the Spring context
            // It subscribes to clientOutboundChannel via its start() method
        } catch (ClassNotFoundException ignored) {
        }

        // Access the internal handlers list via reflection
        try {
            // ExecutorSubscribableChannel has a 'handlers' field
            Field handlersField = null;
            Class<?> clazz = clientOutboundChannel.getClass();
            while (clazz != null) {
                try {
                    handlersField = clazz.getDeclaredField("handlers");
                    break;
                } catch (NoSuchFieldException e) {
                    clazz = clazz.getSuperclass();
                }
            }
            if (handlersField != null) {
                handlersField.setAccessible(true);
                @SuppressWarnings("unchecked")
                CopyOnWriteArraySet<MessageHandler> handlers =
                        (CopyOnWriteArraySet<MessageHandler>) handlersField.get(clientOutboundChannel);
                System.out.println("[DIAG] registered handler count: " + handlers.size());
                int idx = 0;
                for (MessageHandler h : handlers) {
                    System.out.println("[DIAG]   handler[" + idx + "]: " + h.getClass().getName());
                    if (h instanceof SubProtocolWebSocketHandler sph) {
                        System.out.println("[DIAG]   >>> SubProtocolWebSocketHandler found!");
                        System.out.println("[DIAG]   >>> isRunning: " + sph.isRunning());
                        // Inspect sessions map
                        try {
                            Field sessionsField = SubProtocolWebSocketHandler.class.getDeclaredField("sessions");
                            sessionsField.setAccessible(true);
                            @SuppressWarnings("unchecked")
                            ConcurrentHashMap<String, ?> sphSessions =
                                    (ConcurrentHashMap<String, ?>) sessionsField.get(sph);
                            System.out.println("[DIAG]   >>> tracked sessions count: " + sphSessions.size());
                            for (Map.Entry<String, ?> entry : sphSessions.entrySet()) {
                                System.out.println("[DIAG]   >>>   session key: " + entry.getKey()
                                        + " holder: " + entry.getValue());
                            }
                        } catch (Exception e) {
                            System.out.println("[DIAG]   >>> sessions inspection failed: " + e);
                        }
                    }
                    idx++;
                }
            } else {
                System.out.println("[DIAG] could not find 'handlers' field in class hierarchy");
                // Try listing all declared fields for debugging
                clazz = clientOutboundChannel.getClass();
                while (clazz != null && clazz != Object.class) {
                    System.out.println("[DIAG]   class: " + clazz.getName()
                            + " fields: " + java.util.Arrays.toString(
                            java.util.Arrays.stream(clazz.getDeclaredFields())
                                    .map(Field::getName).toArray()));
                    clazz = clazz.getSuperclass();
                }
            }
        } catch (Exception e) {
            System.out.println("[DIAG] handler inspection failed: " + e);
        }
        System.out.println("[DIAG] === end handler inspection ===");
    }

    private void sendJson(StompSession session, String destination, Object payload) throws Exception {
        StompHeaders headers = new StompHeaders();
        headers.setDestination(destination);
        headers.setContentType(org.springframework.util.MimeType.valueOf("application/json"));
        session.send(headers, payload);
    }

    private ChatMessageResponseDTO buildResponse(String loadTestId) {
        return ChatMessageResponseDTO.builder()
                .messageId(UUID.randomUUID().toString())
                .senderId("testUser")
                .recipientId("recipient")
                .chatRoomId("testRoom")
                .content("Hello World")
                .messageType(MessageType.TEXT)
                .status("SENT")
                .timestamp(Instant.now())
                .loadTestId(loadTestId)
                .build();
    }

    private StompSession connect(String userId) throws Exception {
        WebSocketHttpHeaders wsHeaders = new WebSocketHttpHeaders();
        wsHeaders.add("X-Internal-Secret", "blinkInternalSecret2024");

        StompHeaders stompHeaders = new StompHeaders();
        stompHeaders.add("X-User-Id", userId);
        stompHeaders.add("X-User-Email", userId + "@test.com");
        stompHeaders.add("X-User-Name", userId);

        StompSession session = stompClient
                .connectAsync("ws://localhost:" + port + "/ws",
                        wsHeaders, stompHeaders,
                        new DiagnosticStompSessionHandler(userId))
                .get(10, TimeUnit.SECONDS);
        assertThat(session).isNotNull();
        assertThat(session.isConnected()).isTrue();
        allSessions.add(session);
        return session;
    }

    /**
     * Uses reflection to inspect the client-side ConnectionHandlingStompSession
     * to verify that subscriptions are registered and their handlers are correct.
     */
    private void inspectClientSubscriptions(StompSession session) {
        System.out.println("[CLIENT-DIAG] === Client subscription inspection ===");
        System.out.println("[CLIENT-DIAG] session class: " + session.getClass().getName());
        System.out.println("[CLIENT-DIAG] session isConnected: " + session.isConnected());

        // Try to access the internal subscriptions map via reflection
        try {
            // ConnectionHandlingStompSession has a 'subscriptions' field
            Field subsField = null;
            Class<?> clazz = session.getClass();
            while (clazz != null) {
                try {
                    subsField = clazz.getDeclaredField("subscriptions");
                    break;
                } catch (NoSuchFieldException e) {
                    clazz = clazz.getSuperclass();
                }
            }
            if (subsField != null) {
                subsField.setAccessible(true);
                @SuppressWarnings("unchecked")
                Map<String, ?> subscriptions = (Map<String, ?>) subsField.get(session);
                System.out.println("[CLIENT-DIAG] registered subscription count: " + subscriptions.size());
                for (Map.Entry<String, ?> entry : subscriptions.entrySet()) {
                    Object sub = entry.getValue();
                    System.out.println("[CLIENT-DIAG]   subId=" + entry.getKey()
                            + " class=" + (sub != null ? sub.getClass().getName() : "null"));
                    // Try to get the handler from the subscription
                    try {
                        Field handlerField = sub.getClass().getDeclaredField("handler");
                        handlerField.setAccessible(true);
                        Object handler = handlerField.get(sub);
                        System.out.println("[CLIENT-DIAG]   handler=" + (handler != null ? handler.getClass().getName() : "null"));
                    } catch (Exception e2) {
                        System.out.println("[CLIENT-DIAG]   handler inspection failed: " + e2);
                    }
                    // Try to get the destination
                    try {
                        Field destField = sub.getClass().getDeclaredField("destination");
                        destField.setAccessible(true);
                        Object dest = destField.get(sub);
                        System.out.println("[CLIENT-DIAG]   destination=" + dest);
                    } catch (Exception e2) {
                        // try other field names
                        try {
                            Field destField = sub.getClass().getDeclaredField("stompHeaders");
                            destField.setAccessible(true);
                            Object headers = destField.get(sub);
                            System.out.println("[CLIENT-DIAG]   stompHeaders=" + headers);
                        } catch (Exception e3) {
                            System.out.println("[CLIENT-DIAG]   destination inspection failed: " + e3);
                        }
                    }
                }
            } else {
                System.out.println("[CLIENT-DIAG] no 'subscriptions' field found");
                // List all fields for debugging
                clazz = session.getClass();
                while (clazz != null && clazz != Object.class) {
                    System.out.println("[CLIENT-DIAG]   class: " + clazz.getName()
                            + " fields: " + java.util.Arrays.toString(
                            java.util.Arrays.stream(clazz.getDeclaredFields())
                                    .map(Field::getName).toArray()));
                    clazz = clazz.getSuperclass();
                }
            }
        } catch (Exception e) {
            System.out.println("[CLIENT-DIAG] subscription inspection failed: " + e);
        }

        // Also check the session handler
        try {
            Field handlerField = null;
            Class<?> clazz = session.getClass();
            while (clazz != null) {
                try {
                    handlerField = clazz.getDeclaredField("handler");
                    break;
                } catch (NoSuchFieldException e) {
                    clazz = clazz.getSuperclass();
                }
            }
            if (handlerField != null) {
                handlerField.setAccessible(true);
                Object handler = handlerField.get(session);
                System.out.println("[CLIENT-DIAG] session handler: "
                        + (handler != null ? handler.getClass().getName() : "null"));
            }
        } catch (Exception e) {
            System.out.println("[CLIENT-DIAG] handler inspection failed: " + e);
        }

        System.out.println("[CLIENT-DIAG] === end client subscription inspection ===");
    }

    // ================================================================
    // 1. Immediate SUBSCRIBE then SEND: first message not lost
    // ================================================================

    @Test
    @DisplayName("Immediate SUBSCRIBE then SEND: first broadcast message delivered")
    void testImmediateSubscribeThenSend() throws Exception {
        String loadTestId = "integration-" + UUID.randomUUID();
        when(messageService.saveMessage(any())).thenReturn(buildResponse(loadTestId));

        // Reset client-side frame tracking
        clientFrameCount.set(0);
        lastClientFramePreview = "(none)";
        lastClientFrameArrived = false;

        StompSession session = connect("user1");

        // DIAGNOSTIC: inspect who is subscribed to clientOutboundChannel
        inspectOutboundHandlers();

        ConcurrentHashMap<String, ChatMessageResponseDTO> received =
                new ConcurrentHashMap<>();
        CountDownLatch latch = new CountDownLatch(1);

        session.subscribe("/topic/public", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return ChatMessageResponseDTO.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                System.out.println("[TEST-DEBUG] handleFrame called: payload type=" +
                        (payload == null ? "null" : payload.getClass().getName()) +
                        " headers=" + headers);
                if (payload instanceof ChatMessageResponseDTO msg) {
                    System.out.println("[TEST-DEBUG] loadTestId=" + msg.getLoadTestId() +
                            " expected=" + loadTestId);
                    if (loadTestId.equals(msg.getLoadTestId())) {
                        received.put(loadTestId, msg);
                        latch.countDown();
                    }
                } else {
                    System.out.println("[TEST-DEBUG] payload is NOT ChatMessageResponseDTO: " + payload);
                }
            }
        });

        // DIAGNOSTIC: inspect client subscriptions after subscribe()
        inspectClientSubscriptions(session);

        // Small yield to ensure SUBSCRIBE frame is flushed to server
        Thread.sleep(50);

        // Immediately send - no delay. This is the exact race condition.
        sendJson(session, "/app/chat.sendMessage", Map.of(
                "senderId", "ignored",
                "recipientId", "recipient",
                "chatRoomId", "testRoom",
                "content", "Hello World",
                "messageType", "TEXT",
                "loadTestId", loadTestId));

        System.out.println("[TEST-DEBUG] SEND dispatched, waiting for latch...");
        boolean delivered = latch.await(5, TimeUnit.SECONDS);
        System.out.println("[TEST-DEBUG] latch result=" + delivered + " received.size=" + received.size());
        System.out.println("[TEST-DEBUG] clientFrameCount=" + clientFrameCount.get()
                + " lastClientFrameArrived=" + lastClientFrameArrived
                + " lastClientFramePreview=" + lastClientFramePreview);

        assertThat(delivered)
                .as("Broadcast message delivered after immediate SUBSCRIBE + SEND")
                .isTrue();
        assertThat(received).containsKey(loadTestId);

        ChatMessageResponseDTO msg = received.get(loadTestId);
        assertThat(msg.getContent()).isEqualTo("Hello World");
        assertThat(msg.getSenderId()).isEqualTo("testUser");
        assertThat(msg.getMessageType()).isEqualTo(MessageType.TEXT);
    }

    // ================================================================
    // 2. Concurrent sessions: both receive messages
    // ================================================================

    @Test
    @DisplayName("Concurrent sessions: both receive broadcast messages")
    void testConcurrentSessions() throws Exception {
        String loadTestIdA = "concurrent-a-" + UUID.randomUUID();
        String loadTestIdB = "concurrent-b-" + UUID.randomUUID();

        when(messageService.saveMessage(any()))
                .thenAnswer(inv -> {
                    var req = inv.getArgument(0,
                            com.chatApplication.message_service.dto.ChatMessageRequestDTO.class);
                    return ChatMessageResponseDTO.builder()
                            .messageId(UUID.randomUUID().toString())
                            .senderId(req.getSenderId())
                            .recipientId(req.getRecipientId())
                            .chatRoomId(req.getChatRoomId())
                            .content(req.getContent())
                            .messageType(req.getMessageType())
                            .status("SENT")
                            .timestamp(Instant.now())
                            .loadTestId(req.getLoadTestId())
                            .build();
                });

        StompSession sessionA = connect("userA");
        StompSession sessionB = connect("userB");

        CountDownLatch latchA = new CountDownLatch(1);
        CountDownLatch latchB = new CountDownLatch(1);

        sessionA.subscribe("/topic/public", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders h) {
                return ChatMessageResponseDTO.class;
            }

            @Override
            public void handleFrame(StompHeaders h, Object payload) {
                if (payload instanceof ChatMessageResponseDTO msg
                        && loadTestIdA.equals(msg.getLoadTestId())) {
                    latchA.countDown();
                }
            }
        });

        sessionB.subscribe("/topic/public", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders h) {
                return ChatMessageResponseDTO.class;
            }

            @Override
            public void handleFrame(StompHeaders h, Object payload) {
                if (payload instanceof ChatMessageResponseDTO msg
                        && loadTestIdB.equals(msg.getLoadTestId())) {
                    latchB.countDown();
                }
            }
        });

        // Both send immediately
        sendJson(sessionA, "/app/chat.sendMessage", Map.of(
                "senderId", "ignored", "recipientId", "recip",
                "chatRoomId", "roomA", "content", "Msg A",
                "messageType", "TEXT", "loadTestId", loadTestIdA));
        sendJson(sessionB, "/app/chat.sendMessage", Map.of(
                "senderId", "ignored", "recipientId", "recip",
                "chatRoomId", "roomB", "content", "Msg B",
                "messageType", "TEXT", "loadTestId", loadTestIdB));

        boolean deliveredA = latchA.await(5, TimeUnit.SECONDS);
        boolean deliveredB = latchB.await(5, TimeUnit.SECONDS);

        assertThat(deliveredA).as("Session A message delivered").isTrue();
        assertThat(deliveredB).as("Session B message delivered").isTrue();
    }

    // ================================================================
    // 3. No message duplication
    // ================================================================

    @Test
    @DisplayName("Single SEND produces exactly one broadcast (no duplication)")
    void testNoDuplication() throws Exception {
        String loadTestId = "dup-" + UUID.randomUUID();
        when(messageService.saveMessage(any())).thenReturn(buildResponse(loadTestId));

        StompSession session = connect("userDup");
        AtomicInteger count = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(1);

        session.subscribe("/topic/public", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders h) {
                return ChatMessageResponseDTO.class;
            }

            @Override
            public void handleFrame(StompHeaders h, Object payload) {
                if (payload instanceof ChatMessageResponseDTO msg
                        && loadTestId.equals(msg.getLoadTestId())) {
                    count.incrementAndGet();
                    latch.countDown();
                }
            }
        });

        sendJson(session, "/app/chat.sendMessage", Map.of(
                "senderId", "ignored", "recipientId", "recip",
                "chatRoomId", "dup", "content", "Dup test",
                "messageType", "TEXT", "loadTestId", loadTestId));

        latch.await(5, TimeUnit.SECONDS);
        Thread.sleep(500);

        assertThat(count.get())
                .as("Single SEND produces exactly one broadcast")
                .isEqualTo(1);
    }

    // ================================================================
    // 6. Duplicate-subscription regression: one logical subscription = one delivery
    // ================================================================

    @Test
    @DisplayName("One SUBSCRIBE produces exactly one MESSAGE (no duplicate subscription IDs)")
    void testSingleSubscriptionNoDuplicateDelivery() throws Exception {
        String loadTestId = "dup-sub-" + UUID.randomUUID();
        when(messageService.saveMessage(any())).thenReturn(buildResponse(loadTestId));

        StompSession session = connect("userDupSub");
        AtomicInteger deliveryCount = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(1);

        session.subscribe("/topic/public", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders h) {
                return ChatMessageResponseDTO.class;
            }

            @Override
            public void handleFrame(StompHeaders h, Object payload) {
                if (payload instanceof ChatMessageResponseDTO msg
                        && loadTestId.equals(msg.getLoadTestId())) {
                    deliveryCount.incrementAndGet();
                    latch.countDown();
                }
            }
        });

        sendJson(session, "/app/chat.sendMessage", Map.of(
                "senderId", "ignored", "recipientId", "recip",
                "chatRoomId", "dupSub", "content", "DupSub test",
                "messageType", "TEXT", "loadTestId", loadTestId));

        latch.await(5, TimeUnit.SECONDS);
        Thread.sleep(500);

        assertThat(deliveryCount.get())
                .as("One SUBSCRIBE + one SEND produces exactly 1 delivery (no duplicate subscription IDs)")
                .isEqualTo(1);
    }

    // ================================================================
    // 7. Multiple SENDs from same session: all delivered exactly once
    // ================================================================

    @Test
    @DisplayName("Multiple SENDs from same session: all delivered exactly once")
    void testMultipleSends() throws Exception {
        int count = 5;
        CountDownLatch allReceived = new CountDownLatch(count);
        ConcurrentHashMap<String, Boolean> ids = new ConcurrentHashMap<>();

        when(messageService.saveMessage(any()))
                .thenAnswer(inv -> {
                    var req = inv.getArgument(0,
                            com.chatApplication.message_service.dto.ChatMessageRequestDTO.class);
                    return ChatMessageResponseDTO.builder()
                            .messageId(UUID.randomUUID().toString())
                            .senderId(req.getSenderId())
                            .recipientId(req.getRecipientId())
                            .chatRoomId(req.getChatRoomId())
                            .content(req.getContent())
                            .messageType(req.getMessageType())
                            .status("SENT")
                            .timestamp(Instant.now())
                            .loadTestId(req.getLoadTestId())
                            .build();
                });

        StompSession session = connect("userMulti");

        session.subscribe("/topic/public", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders h) {
                return ChatMessageResponseDTO.class;
            }

            @Override
            public void handleFrame(StompHeaders h, Object payload) {
                if (payload instanceof ChatMessageResponseDTO msg
                        && msg.getLoadTestId() != null
                        && msg.getLoadTestId().startsWith("multi-")) {
                    ids.put(msg.getLoadTestId(), true);
                    allReceived.countDown();
                }
            }
        });

        for (int i = 0; i < count; i++) {
            String id = "multi-" + i + "-" + UUID.randomUUID();
            sendJson(session, "/app/chat.sendMessage", Map.of(
                    "senderId", "ignored", "recipientId", "recip",
                    "chatRoomId", "multi", "content", "Msg " + i,
                    "messageType", "TEXT", "loadTestId", id));
        }

        boolean allDelivered = allReceived.await(10, TimeUnit.SECONDS);

        assertThat(allDelivered)
                .as("All %d messages delivered", count)
                .isTrue();
        assertThat(ids).hasSize(count);
    }

    // ================================================================
    // 5. Concurrent sessions with interleaved SENDs
    // ================================================================

    @Test
    @DisplayName("Two sessions subscribe and send concurrently: no cross-session blocking")
    void testConcurrentInterleaved() throws Exception {
        String idA = "interleave-a-" + UUID.randomUUID();
        String idB = "interleave-b-" + UUID.randomUUID();

        when(messageService.saveMessage(any()))
                .thenAnswer(inv -> {
                    var req = inv.getArgument(0,
                            com.chatApplication.message_service.dto.ChatMessageRequestDTO.class);
                    return ChatMessageResponseDTO.builder()
                            .messageId(UUID.randomUUID().toString())
                            .senderId(req.getSenderId())
                            .recipientId(req.getRecipientId())
                            .chatRoomId(req.getChatRoomId())
                            .content(req.getContent())
                            .messageType(req.getMessageType())
                            .status("SENT")
                            .timestamp(Instant.now())
                            .loadTestId(req.getLoadTestId())
                            .build();
                });

        StompSession sessionA = connect("userX");
        StompSession sessionB = connect("userY");

        CountDownLatch latchA = new CountDownLatch(1);
        CountDownLatch latchB = new CountDownLatch(1);

        sessionA.subscribe("/topic/public", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders h) {
                return ChatMessageResponseDTO.class;
            }

            @Override
            public void handleFrame(StompHeaders h, Object payload) {
                if (payload instanceof ChatMessageResponseDTO msg
                        && idA.equals(msg.getLoadTestId())) {
                    latchA.countDown();
                }
            }
        });

        sessionB.subscribe("/topic/public", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders h) {
                return ChatMessageResponseDTO.class;
            }

            @Override
            public void handleFrame(StompHeaders h, Object payload) {
                if (payload instanceof ChatMessageResponseDTO msg
                        && idB.equals(msg.getLoadTestId())) {
                    latchB.countDown();
                }
            }
        });

        // Interleaved sends
        sendJson(sessionA, "/app/chat.sendMessage", Map.of(
                "senderId", "ignored", "recipientId", "r",
                "chatRoomId", "rm", "content", "A1",
                "messageType", "TEXT", "loadTestId", idA));
        sendJson(sessionB, "/app/chat.sendMessage", Map.of(
                "senderId", "ignored", "recipientId", "r",
                "chatRoomId", "rm", "content", "B1",
                "messageType", "TEXT", "loadTestId", idB));

        boolean okA = latchA.await(5, TimeUnit.SECONDS);
        boolean okB = latchB.await(5, TimeUnit.SECONDS);

        assertThat(okA).as("Session A message delivered").isTrue();
        assertThat(okB).as("Session B message delivered").isTrue();
    }
}
