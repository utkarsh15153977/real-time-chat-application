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
 * Negative Test: Wrong Subscription Topic
 * ============================================================
 *
 * Purpose: Prove that the accounting model detects missing
 * deliveries when a VU subscribes to the wrong topic.
 *
 * Flow:
 *   1. VU connects and subscribes to /topic/wrong-destination
 *   2. VU sends 1 message via /app/chat.sendMessage
 *   3. Server broadcasts to /topic/public (VU not subscribed)
 *   4. Server broadcasts to /topic/room.{chatRoomId} (VU not subscribed)
 *   5. VU receives NOTHING on /topic/wrong-destination
 *   6. Accounting: sent=1, received=0, missing=1
 *
 * Expected result:
 *   - SEND succeeds (message persisted by server)
 *   - actual delivery = 0
 *   - missing delivery > 0
 *   - check() detects the failure
 *   - threshold on missing_deliveries causes k6 exit code 99
 *
 * This proves:
 *   - The accounting model correctly detects missing deliveries
 *   - check() assertions identify the specific failure
 *   - k6 exits non-zero when the invariant is violated
 */


/*
 * ============================================================
 * Configuration
 * ============================================================
 */

const WS_URL = getWebSocketUrl();

const SUBSCRIPTION_SYNC_DELAY_MS = 100;

const DELIVERY_TIMEOUT_MS = 3000;


/*
 * ============================================================
 * Custom Metrics
 * ============================================================
 */

export const auditHandshakeFailures = new Counter(
    'audit_neg_handshake_failures'
);

export const auditStompConnectionFailures = new Counter(
    'audit_neg_stomp_connection_failures'
);

export const auditMessageFailures = new Counter(
    'audit_neg_message_failures'
);

export const auditMessagesSent = new Counter(
    'audit_neg_msgs_sent'
);

export const auditMessagesReceived = new Counter(
    'audit_neg_msgs_received'
);

export const auditUniqueReceived = new Counter(
    'audit_neg_unique_received'
);

export const auditDuplicateDeliveries = new Counter(
    'audit_neg_duplicate_deliveries'
);

export const auditMissingDeliveries = new Counter(
    'audit_neg_missing_deliveries'
);

export const auditOwnEchoReceived = new Counter(
    'audit_neg_own_echo_received'
);

export const auditCrossVUReceived = new Counter(
    'audit_neg_cross_vu_received'
);

export const wsMessageLatency = new Trend(
    'ws_message_latency',
    true
);

export const wsConnectionDuration = new Trend(
    'ws_connection_duration',
    true
);


/*
 * ============================================================
 * k6 Options
 * ============================================================
 *
 * Threshold on missing_deliveries: count==0.
 * This will FAIL (exit code 99) because missing > 0.
 *
 * This is the ONLY threshold used in the negative test.
 * It proves that the accounting model can cause a test
 * failure when deliveries are missing.
 */

export const options = {
    scenarios: {
        negative_test: {
            executor: 'shared-iterations',
            vus: 1,
            iterations: 1,
            maxDuration: '30s',
        },
    },

    thresholds: {
        audit_neg_missing_deliveries: [
            'count==0',
        ],

        checks: [
            'rate===1',
        ],
    },
};


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
    cleaned = cleaned.replace(/\u0000$/, '');
    cleaned = cleaned.replace(/^\n+/, '');

    if (!cleaned) {
        return null;
    }

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
 * handleSummary (audit output)
 * ============================================================
 */

export function handleSummary(data) {

    const sent =
        data.metrics.audit_neg_msgs_sent?.values.count || 0;

    const uniqueReceived =
        data.metrics.audit_neg_unique_received?.values.count || 0;

    const duplicates =
        data.metrics.audit_neg_duplicate_deliveries?.values.count || 0;

    const missing =
        data.metrics.audit_neg_missing_deliveries?.values.count || 0;

    const ownEcho =
        data.metrics.audit_neg_own_echo_received?.values.count || 0;

    const crossVU =
        data.metrics.audit_neg_cross_vu_received?.values.count || 0;

    const wsFailures =
        data.metrics.audit_neg_message_failures?.values.count || 0;

    const stompErrors =
        data.metrics.audit_neg_stomp_connection_failures?.values.count || 0;

    const checksPasses =
        data.metrics.checks?.values.passes || 0;

    const checksFails =
        data.metrics.checks?.values.fails || 0;


    const negativeAudit = {
        description: 'Negative test: wrong subscription topic',
        expectedBehavior: 'SEND succeeds, but delivery = 0',
        totalSent: sent,
        totalUniqueReceived: uniqueReceived,
        totalOwnEcho: ownEcho,
        totalCrossVU: crossVU,
        totalDuplicates: duplicates,
        totalMissing: missing,
        totalProtocolErrors: stompErrors + wsFailures,
        checksPasses,
        checksFails,
        result: {
            sendSucceeded: sent > 0,
            deliveryWasZero: uniqueReceived === 0,
            missingWasDetected: missing > 0,
            accountingCorrect:
                sent > 0 && uniqueReceived === 0 && missing > 0,
        },
    };


    console.log('');
    console.log('========================================');
    console.log('  NEGATIVE TEST: ACCOUNTING AUDIT');
    console.log('========================================');
    console.log(JSON.stringify(negativeAudit, null, 2));
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

    let stompConnected = false;
    let subscriptionReady = false;
    let shuttingDown = false;
    let connectionClosed = false;


    /*
     * ==========================================================
     * Per-VU Accounting (LOCAL variables)
     * ==========================================================
     */

    const receivedSet = new Set();

    let localSentCount = 0;
    let localDuplicateCount = 0;
    let localUniqueReceived = 0;
    let localOwnEcho = 0;
    let localCrossVU = 0;
    let localProtocolErrors = 0;

    const pendingMessages = {};

    const iterationId =
        `${__VU}-${__ITER}-${Date.now()}`;


    console.log('');
    console.log('====================================================');
    console.log(
        `[VU ${__VU}] NEGATIVE TEST: Wrong subscription topic`
    );
    console.log(
        `[VU ${__VU}] Will subscribe to /topic/wrong-destination`
    );
    console.log(
        `[VU ${__VU}] Server broadcasts to /topic/public (not subscribed)`
    );
    console.log('====================================================');
    console.log('');


    /*
     * ==========================================================
     * WebSocket Connection
     * ==========================================================
     */

    const params = {
        headers: {
            'Sec-WebSocket-Protocol': 'v12.stomp',
            'X-Internal-Secret':
                ENV.INTERNAL_SECRET,
        },
    };

    const response = ws.connect(
        WS_URL,
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
             * MESSAGE handler
             * ==================================================
             */

            socket.on(
                'message',
                function (rawData) {

                    const stompFrame =
                        parseStompFrame(rawData);

                    if (!stompFrame) {
                        return;
                    }

                    console.log(
                        `[VU ${__VU}] STOMP command: ${stompFrame.command}`
                    );


                    if (
                        stompFrame.command === 'CONNECTED'
                    ) {

                        stompConnected = true;

                        console.log(
                            `[VU ${__VU}] STOMP CONNECTED.`
                        );


                        /*
                         * SUBSCRIBE to WRONG topic
                         */

                        const subscribeFrame =
                            buildStompFrame(
                                'SUBSCRIBE',
                                {
                                    id:
                                        `sub-${__VU}-${__ITER}`,
                                    destination:
                                        '/topic/wrong-destination',
                                    ack: 'auto',
                                }
                            );

                        sendStompFrame(
                            socket,
                            subscribeFrame,
                            'STOMP SUBSCRIBE (WRONG topic)'
                        );


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
                                    `[VU ${__VU}] SUBSCRIPTION READY (wrong topic)`
                                );


                                /*
                                 * SEND 1 message
                                 */

                                const chatMessage =
                                    generateChatMessage(
                                        __VU
                                    );

                                chatMessage.loadTestId =
                                    `${iterationId}-msg-1`;

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
                                    'STOMP SEND (1 message)'
                                );

                                localSentCount++;
                                auditMessagesSent.add(1);

                                console.log(
                                    `[VU ${__VU}] SENT msgId=${chatMessage.loadTestId}`
                                );
                                console.log(
                                    `[VU ${__VU}] Server will broadcast to /topic/public`
                                );
                                console.log(
                                    `[VU ${__VU}] But we subscribed to /topic/wrong-destination`
                                );


                                /*
                                 * Wait for delivery timeout,
                                 * then run assertions.
                                 */

                                socket.setTimeout(
                                    function () {

                                        runAssertions(
                                            localSentCount,
                                            localUniqueReceived,
                                            localOwnEcho,
                                            localCrossVU,
                                            localDuplicateCount,
                                            localProtocolErrors
                                        );

                                        shuttingDown = true;

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

                                        socket.setTimeout(
                                            function () {
                                                if (
                                                    !connectionClosed
                                                ) {
                                                    socket.close();
                                                }
                                            },
                                            500
                                        );

                                    },
                                    DELIVERY_TIMEOUT_MS
                                );

                            },
                            SUBSCRIPTION_SYNC_DELAY_MS
                        );
                    }


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
                                return;
                            }

                            if (
                                receivedSet.has(
                                    receivedLoadTestId
                                )
                            ) {
                                localDuplicateCount++;
                                auditDuplicateDeliveries.add(1);
                                return;
                            }

                            receivedSet.add(
                                receivedLoadTestId
                            );

                            localUniqueReceived++;
                            auditUniqueReceived.add(1);

                            const isOwnMessage =
                                receivedLoadTestId.startsWith(
                                    `${iterationId}-`
                                );

                            if (isOwnMessage) {
                                localOwnEcho++;
                                auditOwnEchoReceived.add(1);
                            } else {
                                localCrossVU++;
                                auditCrossVUReceived.add(1);
                            }

                            const sentTimestamp =
                                pendingMessages[
                                    receivedLoadTestId
                                ];

                            if (
                                sentTimestamp !== undefined
                            ) {
                                const latency =
                                    Date.now() -
                                    sentTimestamp;

                                if (latency >= 0) {
                                    wsMessageLatency.add(
                                        latency
                                    );
                                }

                                delete pendingMessages[
                                    receivedLoadTestId
                                ];
                            }


                        } catch (error) {

                            console.error(
                                `[VU ${__VU}] Parse error: ${error}`
                            );

                            auditMessageFailures.add(1);
                            localProtocolErrors++;
                        }
                    }


                    else if (
                        stompFrame.command === 'ERROR'
                    ) {

                        if (shuttingDown) {
                            return;
                        }

                        console.error(
                            `[VU ${__VU}] UNEXPECTED STOMP ERROR`
                        );

                        auditStompConnectionFailures.add(1);
                        localProtocolErrors++;

                        socket.close();
                    }
                }
            );


            socket.on(
                'error',
                function (error) {

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


            socket.on(
                'close',
                function () {

                    connectionClosed = true;

                    const duration =
                        Date.now() - startTime;

                    wsConnectionDuration.add(duration);

                    console.log(
                        `[VU ${__VU}] WebSocket closed.`
                    );

                    if (!stompConnected) {
                        auditStompConnectionFailures.add(1);
                        localProtocolErrors++;
                    }
                }
            );
        }
    );


    /*
     * ==========================================================
     * Handshake Validation
     * ==========================================================
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

    if (!handshakeSuccessful) {
        auditHandshakeFailures.add(1);
    }
}


/*
 * ============================================================
 * Per-VU Assertions (Negative Test)
 * ============================================================
 */

function runAssertions(
    localSentCount,
    localUniqueReceived,
    localOwnEcho,
    localCrossVU,
    localDuplicateCount,
    localProtocolErrors
) {

    const localMissing =
        Math.max(
            0,
            localSentCount - localUniqueReceived
        );

    console.log('');
    console.log(
        `[VU ${__VU}] ── NEGATIVE TEST ACCOUNTING ──`
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
    console.log('');


    if (localMissing > 0) {
        auditMissingDeliveries.add(localMissing);
    }


    /*
     * Assertions for the negative test.
     *
     * We expect:
     *   - SEND succeeded (sent == 1)
     *   - No messages received (uniqueReceived == 0)
     *   - Missing > 0
     *   - The check "delivery detected" MUST FAIL
     */

    check(
        { vu: __VU },
        {
            'SEND was successful':
                (v) => localSentCount === 1,

            'expected delivery > 0':
                (v) => localSentCount > 0,

            'actual delivery == 0 (wrong topic)':
                (v) => localUniqueReceived === 0,

            'missing delivery > 0 (detected)':
                (v) => localMissing > 0,

            'OVERRIDE: this check SHOULD FAIL - proves detection works':
                (v) => localUniqueReceived > 0,
        }
    );
}
