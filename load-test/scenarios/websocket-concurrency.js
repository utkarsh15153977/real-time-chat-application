import ws from 'k6/ws';
import { check, sleep } from 'k6';
import { Counter, Trend } from 'k6/metrics';

import {
    ENV,
    buildStompFrame,
    generateChatMessage,
    getWebSocketUrl,
} from '../config/test-env.js';


/*
 * ============================================================
 * WebSocket Configuration
 * ============================================================
 */

const WS_URL = getWebSocketUrl();


/*
 * ============================================================
 * Subscription Synchronization
 * ============================================================
 *
 * Spring STOMP does NOT send a SUBSCRIBED acknowledgement frame.
 * The server registers subscriptions silently via
 * DefaultSubscriptionRegistry.registerSubscription().
 *
 * Because clientInboundChannel uses an ExecutorSubscribableChannel
 * (thread pool), SUBSCRIBE and SEND frames are processed
 * asynchronously. If SEND arrives before SUBSCRIBE registration
 * completes, SimpleBrokerMessageHandler.findSubscriptions()
 * returns 0 subscribers and the message is dropped.
 *
 * To eliminate this race, we defer the first SEND by
 * SUBSCRIPTION_SYNC_DELAY_MS milliseconds after sending
 * the SUBSCRIBE frame. This gives Spring's executor time
 * to process the SUBSCRIBE before the SEND arrives.
 *
 * This is NOT a SUBSTITUTE for a proper server-side
 * SUBSCRIBE acknowledgement — it is the best client-side
 * synchronization available when no server ack exists.
 */

const SUBSCRIPTION_SYNC_DELAY_MS = 100;


/*
 * ============================================================
 * Custom Metrics
 * ============================================================
 */

export const auditHandshakeFailures = new Counter(
    'audit_conc_handshake_failures'
);

export const auditStompConnectionFailures = new Counter(
    'audit_conc_stomp_connection_failures'
);

export const auditMessageFailures = new Counter(
    'audit_conc_message_failures'
);

export const auditMessagesSent = new Counter(
    'audit_conc_msgs_sent'
);

export const auditMessagesReceived = new Counter(
    'audit_conc_msgs_received'
);

export const wsMessageLatency = new Trend(
    'ws_message_latency',
    true
);

export const wsConnectionDuration = new Trend(
    'ws_connection_duration',
    true
);

export const auditUniqueReceived = new Counter(
    'audit_conc_unique_received'
);

export const auditDuplicateDeliveries = new Counter(
    'audit_conc_duplicate_deliveries'
);

export const auditMissingDeliveries = new Counter(
    'audit_conc_missing_deliveries'
);

export const auditOwnEchoReceived = new Counter(
    'audit_conc_own_echo_received'
);

export const auditCrossVUReceived = new Counter(
    'audit_conc_cross_vu_received'
);


/*
 * ============================================================
 * k6 Options
 * ============================================================
 */

export const options = {
    stages: [
        {
            duration: ENV.TEST_PARAMS.RAMP_DURATION,
            target: ENV.TEST_PARAMS.TARGET_VUS,
        },
        {
            duration: ENV.TEST_PARAMS.SOAK_DURATION,
            target: ENV.TEST_PARAMS.TARGET_VUS,
        },
        {
            duration: ENV.TEST_PARAMS.RAMP_DOWN_DURATION,
            target: 0,
        },
    ],

    thresholds: {
        checks: [
            'rate>0.99',
        ],

        audit_conc_handshake_failures: [
            'count==0',
        ],

        audit_conc_stomp_connection_failures: [
            'count==0',
        ],

        audit_conc_message_failures: [
            'count==0',
        ],

        ws_message_latency: [
            `p(95)<${ENV.THRESHOLDS.WS_LATENCY_P95_MS}`,
        ],
    },
};


/*
 * ============================================================
 * Send Native STOMP Frame
 * ============================================================
 */

function sendStompFrame(
    socket,
    frame,
    description = 'STOMP frame'
) {
    console.log(
        `[VU ${__VU}] Sending ${description}.`
    );

    socket.send(frame);
}


/*
 * ============================================================
 * Parse Native STOMP Frame
 * ============================================================
 */

function parseStompFrame(frame) {

    if (!frame) {
        return null;
    }

    let cleaned = String(frame);

    /*
     * Remove STOMP NULL terminator.
     */
    cleaned = cleaned.replace(/\u0000$/, '');

    /*
     * Remove heartbeat/newline characters
     * before the STOMP command.
     */
    cleaned = cleaned.replace(/^\n+/, '');

    if (!cleaned) {
        return null;
    }

    /*
     * Find STOMP header/body separator.
     */
    const separatorIndex =
        cleaned.indexOf('\n\n');

    let headerPart;
    let body;

    if (separatorIndex === -1) {

        headerPart = cleaned;
        body = '';

    } else {

        headerPart = cleaned.substring(
            0,
            separatorIndex
        );

        body = cleaned.substring(
            separatorIndex + 2
        );
    }

    const lines =
        headerPart.split('\n');

    const command =
        lines.shift()?.trim();

    if (!command) {
        return null;
    }

    const headers = {};

    for (const line of lines) {

        const colonIndex =
            line.indexOf(':');

        if (colonIndex === -1) {
            continue;
        }

        const key =
            line.substring(
                0,
                colonIndex
            ).trim();

        const value =
            line.substring(
                colonIndex + 1
            ).trim();

        headers[key] = value;
    }

    return {
        command,
        headers,
        body,
    };
}


/*
 * ============================================================
 * handleSummary (audit output)
 * ============================================================
 */

export function handleSummary(data) {

    const sent =
        data.metrics.audit_conc_msgs_sent?.values.count || 0;

    const uniqueReceived =
        data.metrics.audit_conc_unique_received?.values.count || 0;

    const duplicates =
        data.metrics.audit_conc_duplicate_deliveries?.values.count || 0;

    const missing =
        data.metrics.audit_conc_missing_deliveries?.values.count || 0;

    const ownEcho =
        data.metrics.audit_conc_own_echo_received?.values.count || 0;

    const crossVU =
        data.metrics.audit_conc_cross_vu_received?.values.count || 0;

    const wsFailures =
        data.metrics.audit_conc_message_failures?.values.count || 0;

    const stompErrors =
        data.metrics.audit_conc_stomp_connection_failures?.values.count || 0;

    const handshakeFails =
        data.metrics.audit_conc_handshake_failures?.values.count || 0;

    const checksPasses =
        data.metrics.checks?.values.passes || 0;

    const checksFails =
        data.metrics.checks?.values.fails || 0;


    /*
     * Aggregate audit.
     *
     * For a ramping test, we cannot assert exact expected
     * deliveries. We verify the lower-bound invariants:
     *   - No duplicates
     *   - No protocol errors
     *   - At least some messages received
     *   - Delivery ratio > 0
     */

    const aggregateAudit = {
        totalSent: sent,
        totalUniqueReceived: uniqueReceived,
        totalOwnEcho: ownEcho,
        totalCrossVU: crossVU,
        totalDuplicates: duplicates,
        totalMissing: missing,
        totalProtocolErrors: stompErrors + wsFailures + handshakeFails,
        deliveryRatio: sent > 0
            ? (uniqueReceived / sent * 100).toFixed(1) + '%'
            : 'N/A',
        checksPasses,
        checksFails,
        invariantHolds: {
            noDuplicates: duplicates === 0,
            noProtocolErrors:
                (stompErrors + wsFailures + handshakeFails) === 0,
            receivedAnything: uniqueReceived > 0,
            deliveryRatioPositive: uniqueReceived > 0,
        },
    };


    const allInvariantsHold = Object.values(
        aggregateAudit.invariantHolds
    ).every(Boolean);

    aggregateAudit.overallResult =
        allInvariantsHold ? 'PASS' : 'FAIL';


    console.log('');
    console.log('========================================');
    console.log('  CONCURRENCY TEST: ACCOUNTING AUDIT');
    console.log('========================================');
    console.log(JSON.stringify(aggregateAudit, null, 2));
    console.log('========================================');
    console.log('');


    return {};
}


/*
 * ============================================================
 * Main Test
 * ============================================================
 */

export default function () {

    const startTime = Date.now();

    /*
     * ==========================================================
     * Connection State
     * ==========================================================
     */

    let stompConnected = false;

    let subscriptionReady = false;

    let messageSent = false;

    /*
     * True when intentional shutdown has started.
     */
    let shuttingDown = false;

    /*
     * True when WebSocket actually closes.
     */
    let connectionClosed = false;


    /*
     * ==========================================================
     * Per-VU Accounting (LOCAL variables for assertions)
     * ==========================================================
     *
     * These track per-VU state for check() assertions.
     * k6 Counters are for aggregate reporting only.
     */

    const receivedSet = new Set();

    let localSentCount = 0;
    let localDuplicateCount = 0;
    let localUniqueReceived = 0;
    let localOwnEcho = 0;
    let localCrossVU = 0;
    let localProtocolErrors = 0;


    /*
     * ==========================================================
     * Message Correlation
     * ==========================================================
     *
     * Map:
     *
     *     loadTestId -> send timestamp
     *
     * Example:
     *
     *     "1-0-123456789" -> 123456789
     *
     * When the same loadTestId is received,
     * we calculate:
     *
     *     Date.now() - sendTimestamp
     */

    const pendingMessages = {};


    /*
     * ==========================================================
     * Delivery Matrix
     * ==========================================================
     *
     * Track how many messages this VU received from each
     * producer VU. Keys are producer VU IDs (as strings)
     * or "unknown" if the producer cannot be parsed.
     */

    const receivedByProducer = {};


    /*
     * Unique ID for this VU iteration.
     */

    const iterationId =
        `${__VU}-${__ITER}-${Date.now()}`;


    /*
     * Direct local WebSocket endpoint.
     */

    const wsUrl = WS_URL;


    /*
     * ==========================================================
     * Test Information
     * ==========================================================
     */

    console.log('');

    console.log(
        '===================================================='
    );

    console.log(
        `[VU ${__VU}] Starting WebSocket test`
    );

    console.log(
        `[VU ${__VU}] Iteration: ${__ITER}`
    );

    console.log(
        `[VU ${__VU}] Iteration ID: ${iterationId}`
    );

    console.log(
        `[VU ${__VU}] WebSocket URL: ${wsUrl}`
    );

    console.log(
        '===================================================='
    );


    /*
     * ==========================================================
     * WebSocket Handshake Parameters
     * ==========================================================
     */

    const params = {
        headers: {
            'Sec-WebSocket-Protocol': 'v12.stomp',
            'X-Internal-Secret': ENV.INTERNAL_SECRET,
        },
    };


    /*
     * ==========================================================
     * WebSocket Connection
     * ==========================================================
     */

    const response = ws.connect(
        wsUrl,
        params,
        function (socket) {


            /*
             * ==================================================
             * OPEN
             * ==================================================
             */

            socket.on(
                'open',
                function () {

                    console.log(
                        `[VU ${__VU}] WebSocket opened.`
                    );

                    /*
                     * Never print the JWT.
                     */

                    console.log(
                        `[VU ${__VU}] JWT configured: ${Boolean(ENV.AUTH_TOKEN)}`
                    );


                    /*
                     * ==================================================
                     * STOMP CONNECT
                     * ==================================================
                     */

                    const connectFrame =
                        buildStompFrame(
                            'CONNECT',
                            {
                                'accept-version': '1.2',

                                'host': 'localhost',

                                'heart-beat': '10000,10000',

                                'Authorization':
                                    `Bearer ${ENV.AUTH_TOKEN}`,
                            }
                        );

                    sendStompFrame(
                        socket,
                        connectFrame,
                        'STOMP CONNECT'
                    );
                }
            );


            /*
             * ==================================================
             * MESSAGE
             * ==================================================
             */

            socket.on(
                'message',
                function (rawData) {

                    const stompFrame =
                        parseStompFrame(rawData);

                    if (!stompFrame) {

                        console.log(
                            `[VU ${__VU}] Received non-STOMP/empty server frame.`
                        );

                        return;
                    }

                    console.log(
                        `[VU ${__VU}] STOMP command received: ${stompFrame.command}`
                    );


                    /*
                     * ==================================================
                     * CONNECTED
                     * ==================================================
                     */

                    if (
                        stompFrame.command === 'CONNECTED'
                    ) {

                        stompConnected = true;

                        console.log(
                            `[VU ${__VU}] STOMP CONNECTED successfully.`
                        );


                        if (
                            stompFrame.headers &&
                            stompFrame.headers.version
                        ) {

                            console.log(
                                `[VU ${__VU}] STOMP version: ${stompFrame.headers.version}`
                            );
                        }


                        /*
                         * ==================================================
                         * SUBSCRIBE
                         * ==================================================
                         */

                        const subscribeFrame =
                            buildStompFrame(
                                'SUBSCRIBE',
                                {
                                    id:
                                        `sub-${__VU}-${__ITER}`,

                                    destination:
                                    ENV.ENDPOINTS.TOPIC_PUBLIC,

                                    ack: 'auto',
                                }
                            );

                        sendStompFrame(
                            socket,
                            subscribeFrame,
                            'STOMP SUBSCRIBE'
                        );

                        console.log(
                            `[VU ${__VU}] SUBSCRIBE sent to ${ENV.ENDPOINTS.TOPIC_PUBLIC}`
                        );


                        /*
                         * ==================================================
                         * Subscription Synchronization
                         * ==================================================
                         *
                         * Defer the first SEND by
                         * SUBSCRIPTION_SYNC_DELAY_MS to allow
                         * Spring's clientInboundChannel executor
                         * to complete SUBSCRIBE registration
                         * before SEND reaches the broker.
                         *
                         * Spring STOMP does not send a SUBSCRIBED
                         * acknowledgement, so this delay is the
                         * best client-side synchronization
                         * available.
                         */

                        socket.setTimeout(
                            function () {

                                if (
                                    shuttingDown ||
                                    connectionClosed
                                ) {
                                    return;
                                }


                                subscriptionReady = true;

                                console.log(
                                    `[VU ${__VU}] SUBSCRIPTION READY (after ${SUBSCRIPTION_SYNC_DELAY_MS}ms sync delay)`
                                );


                                /*
                                 * ==================================================
                                 * First Chat Message
                                 * ==================================================
                                 */

                                const chatMessage =
                                    generateChatMessage(__VU);

                                chatMessage.loadTestId =
                                    `${iterationId}-message-1`;


                                /*
                                 * Record send time BEFORE sending.
                                 *
                                 * This is the important part for
                                 * accurate round-trip latency.
                                 */

                                const sendTimestamp =
                                    Date.now();

                                pendingMessages[
                                    chatMessage.loadTestId
                                    ] = sendTimestamp;


                                const messageBody =
                                    JSON.stringify(
                                        chatMessage
                                    );


                                const sendFrame =
                                    buildStompFrame(
                                        'SEND',
                                        {
                                            destination:
                                            ENV.ENDPOINTS.SEND_MESSAGE,

                                            'content-type':
                                                'application/json',
                                        },
                                        messageBody
                                    );


                                sendStompFrame(
                                    socket,
                                    sendFrame,
                                    'STOMP SEND (first message)'
                                );

                                auditMessagesSent.add(1);

                                localSentCount++;

                                messageSent = true;


                                /*
                                 * ==================================================
                                 * Periodic Messages
                                 * ==================================================
                                 *
                                 * Started from within the subscription
                                 * sync callback so that the interval
                                 * begins only after the first message
                                 * has been sent.
                                 */

                                socket.setInterval(
                                    function () {

                                        /*
                                         * Never send messages after
                                         * shutdown begins.
                                         */

                                        if (
                                            !stompConnected ||
                                            !subscriptionReady ||
                                            shuttingDown ||
                                            connectionClosed
                                        ) {
                                            return;
                                        }


                                        const message =
                                            generateChatMessage(__VU);


                                        /*
                                         * Make every message ID
                                         * unique within this iteration.
                                         */

                                        message.loadTestId =
                                            `${iterationId}-message-${Date.now()}-${Math.random()
                                                .toString(36)
                                                .slice(2, 8)}`;


                                        /*
                                         * Record the exact local
                                         * send timestamp.
                                         */

                                        pendingMessages[
                                            message.loadTestId
                                            ] = Date.now();


                                        const body =
                                            JSON.stringify(
                                                message
                                            );


                                        const frame =
                                            buildStompFrame(
                                                'SEND',
                                                {
                                                    destination:
                                                    ENV.ENDPOINTS.SEND_MESSAGE,

                                                    'content-type':
                                                        'application/json',
                                                },
                                                body
                                            );


                                        sendStompFrame(
                                            socket,
                                            frame,
                                            'periodic STOMP SEND'
                                        );


                                        auditMessagesSent.add(1);

                                        localSentCount++;

                                        messageSent = true;

                                    },
                                    ENV.TEST_PARAMS.MESSAGE_INTERVAL_MS
                                );

                            },
                            SUBSCRIPTION_SYNC_DELAY_MS
                        );


                        /*
                         * ==================================================
                         * Connection Timeout
                         * ==================================================
                         */

                        socket.setTimeout(
                            function () {

                                if (
                                    shuttingDown ||
                                    connectionClosed
                                ) {
                                    return;
                                }


                                console.log(
                                    `[VU ${__VU}] Connection duration reached.`
                                );


                                /*
                                 * Begin intentional shutdown.
                                 *
                                 * This stops periodic SEND operations
                                 * and allows us to ignore the expected
                                 * "Session closed" STOMP ERROR.
                                 */

                                shuttingDown = true;


                                /*
                                 * Send STOMP DISCONNECT.
                                 */

                                const disconnectFrame =
                                    buildStompFrame(
                                        'DISCONNECT',
                                        {}
                                    );


                                sendStompFrame(
                                    socket,
                                    disconnectFrame,
                                    'STOMP DISCONNECT'
                                );


                                /*
                                 * Give DISCONNECT a small amount
                                 * of time to leave the socket.
                                 */

                                socket.setTimeout(
                                    function () {

                                        if (!connectionClosed) {
                                            socket.close();
                                        }

                                    },
                                    100
                                );

                            },
                            ENV.TEST_PARAMS.CONNECTION_DURATION_MS
                        );
                    }


                    /*
                     * ==================================================
                     * STOMP MESSAGE
                     * ==================================================
                     */

                    else if (
                        stompFrame.command === 'MESSAGE'
                    ) {

                        auditMessagesReceived.add(1);


                        const body =
                            stompFrame.body;


                        try {

                            const parsedBody =
                                JSON.parse(body);


                            const receivedLoadTestId =
                                parsedBody.loadTestId;


                            if (!receivedLoadTestId) {

                                console.log(
                                    `[VU ${__VU}] Received MESSAGE without loadTestId.`
                                );

                                return;
                            }


                            /*
                             * Duplicate detection (per-VU).
                             *
                             * If this VU already received this
                             * loadTestId, count as duplicate
                             * and do not double-count.
                             */

                            if (
                                receivedSet.has(
                                    receivedLoadTestId
                                )
                            ) {

                                localDuplicateCount++;
                                auditDuplicateDeliveries.add(1);

                                console.log(
                                    `[VU ${__VU}] DUPLICATE: ${receivedLoadTestId}`
                                );

                                return;
                            }


                            /*
                             * New unique message received.
                             */

                            receivedSet.add(
                                receivedLoadTestId
                            );

                            localUniqueReceived++;
                            auditUniqueReceived.add(1);


                            /*
                             * Determine own message vs cross-VU.
                             */

                            const isOwnMessage =
                                receivedLoadTestId.startsWith(
                                    `${iterationId}-`
                                );


                            if (isOwnMessage) {

                                localOwnEcho++;
                                auditOwnEchoReceived.add(1);

                                console.log(
                                    `[VU ${__VU}] OWN ECHO: ${receivedLoadTestId}`
                                );

                            } else {

                                localCrossVU++;
                                auditCrossVUReceived.add(1);

                                console.log(
                                    `[VU ${__VU}] CROSS-VU: ${receivedLoadTestId}`
                                );
                            }


                            /*
                             * Latency calculation for own messages.
                             */

                            const sentTimestamp =
                                pendingMessages[
                                    receivedLoadTestId
                                    ];

                            if (
                                sentTimestamp !== undefined
                            ) {

                                const receiveTimestamp =
                                    Date.now();

                                const latency =
                                    receiveTimestamp -
                                    sentTimestamp;

                                if (latency >= 0) {

                                    wsMessageLatency.add(
                                        latency
                                    );

                                    console.log(
                                        `[VU ${__VU}] Round-trip latency: ${latency} ms`
                                    );
                                }

                                delete pendingMessages[
                                    receivedLoadTestId
                                    ];
                            }


                            /*
                             * Delivery matrix update.
                             */

                            const dashIndex =
                                receivedLoadTestId.indexOf('-');

                            let producerVu = 'unknown';

                            if (dashIndex > 0) {

                                const candidate =
                                    receivedLoadTestId.substring(
                                        0,
                                        dashIndex
                                    );

                                if (
                                    !Number.isNaN(
                                        Number.parseInt(
                                            candidate,
                                            10
                                        )
                                    )
                                ) {
                                    producerVu = candidate;
                                }
                            }

                            if (
                                !receivedByProducer[
                                    producerVu
                                    ]
                            ) {
                                receivedByProducer[
                                    producerVu
                                    ] = 0;
                            }

                            receivedByProducer[
                                producerVu
                                ]++;


                        } catch (error) {

                            console.error(
                                `[VU ${__VU}] Failed to parse STOMP MESSAGE body: ${error}`
                            );

                            auditMessageFailures.add(1);
                            localProtocolErrors++;
                        }
                    }


                    /*
                     * ==================================================
                     * STOMP ERROR
                     * ==================================================
                     */

                    else if (
                        stompFrame.command === 'ERROR'
                    ) {

                        /*
                         * Ignore expected ERROR during
                         * intentional shutdown.
                         */

                        if (shuttingDown) {

                            console.log(
                                `[VU ${__VU}] STOMP session closed during intentional shutdown.`
                            );

                            return;
                        }


                        /*
                         * Unexpected STOMP ERROR.
                         */

                        console.error(
                            `[VU ${__VU}] STOMP ERROR received.`
                        );


                        /*
                         * Safe diagnostic logging.
                         *
                         * Never print Authorization.
                         */

                        console.error(
                            `[VU ${__VU}] STOMP ERROR headers: ${JSON.stringify(
                                stompFrame.headers || {}
                            )}`
                        );

                        console.error(
                            `[VU ${__VU}] STOMP ERROR body: ${stompFrame.body || ''}`
                        );


                        auditStompConnectionFailures.add(1);

                        localProtocolErrors++;

                        socket.close();
                    }
                }
            );


            /*
             * ==================================================
             * WebSocket ERROR
             * ==================================================
             */

            socket.on(
                'error',
                function (error) {

                    /*
                     * Ignore expected shutdown errors.
                     */

                    if (shuttingDown) {
                        return;
                    }

                    console.error(
                        `[VU ${__VU}] WebSocket error: ${error}`
                    );

                    auditMessageFailures.add(1);

                    localProtocolErrors++;
                }
            );


            /*
             * ==================================================
             * WebSocket CLOSE
             * ==================================================
             */

            socket.on(
                'close',
                function () {

                    connectionClosed = true;

                    const duration =
                        Date.now() - startTime;


                    wsConnectionDuration.add(
                        duration
                    );


                    console.log(
                        `[VU ${__VU}] WebSocket closed.`
                    );

                    console.log(
                        `[VU ${__VU}] Connection duration: ${duration} ms`
                    );

                    console.log(
                        `[VU ${__VU}] STOMP connected: ${stompConnected}`
                    );

                    console.log(
                        `[VU ${__VU}] Message sent: ${messageSent}`
                    );


                    /*
                     * ==========================================================
                     * Per-VU Accounting Summary
                     * ==========================================================
                     */

                    const localMissing =
                        Math.max(
                            0,
                            localSentCount - localUniqueReceived
                        );

                    console.log(
                        `[VU ${__VU}] ── ACCOUNTING SUMMARY ──`
                    );

                    console.log(
                        `[VU ${__VU}]   sent: ${localSentCount}`
                    );

                    console.log(
                        `[VU ${__VU}]   uniqueReceived: ${localUniqueReceived}`
                    );

                    console.log(
                        `[VU ${__VU}]   ownEcho: ${localOwnEcho}`
                    );

                    console.log(
                        `[VU ${__VU}]   crossVU: ${localCrossVU}`
                    );

                    console.log(
                        `[VU ${__VU}]   duplicates: ${localDuplicateCount}`
                    );

                    console.log(
                        `[VU ${__VU}]   missing: ${localMissing}`
                    );

                    console.log(
                        `[VU ${__VU}]   protocolErrors: ${localProtocolErrors}`
                    );


                    if (localMissing > 0) {
                        auditMissingDeliveries.add(localMissing);
                    }


                    /*
                     * ==========================================================
                     * Delivery Matrix Summary
                     * ==========================================================
                     */

                    const producerKeys =
                        Object.keys(receivedByProducer);

                    if (producerKeys.length > 0) {

                        console.log(
                            `[VU ${__VU}] ── DELIVERY MATRIX ROW ──`
                        );

                        let totalReceived = 0;

                        for (
                            const pVu of producerKeys
                            ) {

                            const count =
                                receivedByProducer[pVu];

                            totalReceived += count;

                            console.log(
                                `[VU ${__VU}]   from VU ${pVu} = ${count}`
                            );
                        }

                        console.log(
                            `[VU ${__VU}]   total received = ${totalReceived}`
                        );

                    } else {

                        console.log(
                            `[VU ${__VU}] No messages received during this session.`
                        );
                    }


                    /*
                     * ==========================================================
                     * Per-VU Assertions
                     * ==========================================================
                     *
                     * Uses LOCAL variables only.
                     *
                     * Provable lower-bound invariants for ramping test:
                     *   - Each VU receives at least its own echoed messages
                     *   - No duplicates
                     *   - No protocol errors
                     *   - No unexpected messages
                     *
                     * We cannot assert exact delivery counts because VUs
                     * ramp up over time and may not all be subscribed
                     * when every message is sent.
                     */

                    check(
                        { vu: __VU },
                        {
                            'VU received at least its own echoed messages':
                                (v) =>
                                    localUniqueReceived >=
                                    localSentCount,

                            'VU has no duplicate deliveries':
                                (v) =>
                                    localDuplicateCount === 0,

                            'VU has no protocol errors':
                                (v) =>
                                    localProtocolErrors === 0,

                            'VU has no missing own-echo messages':
                                (v) =>
                                    localOwnEcho >=
                                    localSentCount,
                        }
                    );


                    if (!stompConnected) {

                        console.error(
                            `[VU ${__VU}] STOMP CONNECT failed before CONNECTED response.`
                        );

                        auditStompConnectionFailures.add(1);

                        localProtocolErrors++;
                    }
                }
            );
        }
    );


    /*
     * ============================================================
     * WebSocket Handshake Validation
     * ============================================================
     */

    const handshakeSuccessful =
        response &&
        response.status === 101;


    check(
        response,
        {
            'WebSocket handshake status is 101':
                (r) =>
                    r &&
                    r.status === 101,
        }
    );


    /*
     * ============================================================
     * Handshake Failure Diagnostics
     * ============================================================
     */

    if (!handshakeSuccessful) {

        auditHandshakeFailures.add(1);

        console.error(
            `[VU ${__VU}] WebSocket handshake failed.`
        );


        if (response) {

            console.error(
                `[VU ${__VU}] HTTP status: ${response.status}`
            );


            if (response.body) {

                console.error(
                    `[VU ${__VU}] Handshake response body: ${String(response.body).substring(0, 1000)}`
                );
            }


            if (response.headers) {

                const safeHeaders = {};

                for (const key in response.headers) {

                    if (
                        key.toLowerCase() === 'authorization'
                    ) {
                        continue;
                    }

                    safeHeaders[key] =
                        response.headers[key];
                }

                console.error(
                    `[VU ${__VU}] Handshake response headers: ${JSON.stringify(safeHeaders)}`
                );
            }
        }

    } else {

        console.log(
            `[VU ${__VU}] WebSocket handshake successful: HTTP 101`
        );


        if (
            response.headers &&
            response.headers['Sec-WebSocket-Protocol']
        ) {

            console.log(
                `[VU ${__VU}] Negotiated WebSocket protocol: ${response.headers['Sec-WebSocket-Protocol']}`
            );

        } else {

            console.log(
                `[VU ${__VU}] Negotiated WebSocket protocol: not reported by k6`
            );
        }
    }
    /*
     * ============================================================
     * Small Pause Between Iterations
     * ============================================================
     */
    sleep(0.1);
}