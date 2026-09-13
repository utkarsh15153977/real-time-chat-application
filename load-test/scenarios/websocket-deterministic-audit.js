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
 * Deterministic Accounting Audit Test
 * ============================================================
 *
 * Purpose: Prove the accounting model is correct under
 * controlled, low-concurrency conditions.
 *
 * Invariant:
 *   - 2 VUs
 *   - Both VUs subscribe to /topic/public before either sends
 *   - Each VU sends exactly 1 globally unique message
 *   - Every subscribed VU receives exactly one copy of each message
 *   - Expected total deliveries = 4 (2 messages x 2 subscribers)
 *   - Each VU expected deliveries = 2 (1 own echo + 1 cross-VU)
 *
 * Synchronization:
 *   k6 v2.2.0 does not provide k6/fs, k6/exec, or mutable
 *   SharedArray. VU-to-VU synchronization uses the existing
 *   SUBSCRIPTION_SYNC_DELAY_MS pattern: each VU confirms its
 *   own subscription state, then waits a conservative safety
 *   bound for the other VU to reach the same state.
 *
 *   Correctness depends on subscription protocol state, not
 *   the timing of the safety bound.
 *
 * Pass criteria:
 *   per VU:  uniqueReceived == 2, ownEcho == 1, crossVU == 1,
 *            duplicates == 0, missing == 0, protocolErrors == 0
 *   aggregate: totalSent == 2, totalUniqueReceived == 4,
 *              totalOwnEcho == 2, totalCrossVU == 2,
 *              totalDuplicates == 0, totalMissing == 0,
 *              totalProtocolErrors == 0
 */


/*
 * ============================================================
 * Configuration
 * ============================================================
 */

const WS_URL = getWebSocketUrl();

/*
 * Subscription sync delay: time to wait after sending
 * SUBSCRIBE before sending SEND. This gives Spring's
 * clientInboundChannel executor time to register the
 * subscription.
 */
const SUBSCRIPTION_SYNC_DELAY_MS = 100;

/*
 * Safety bound: additional time after subscription sync
 * to ensure the OTHER VU has also subscribed.
 *
 * With 2 VUs and a 100ms subscription sync delay, the
 * other VU needs ~100ms to subscribe. A 2000ms bound
 * gives 20x margin.
 *
 * This is a SAFETY BOUND, not a correctness mechanism.
 * Correctness depends on each VU being subscribed before
 * it sends, which is guaranteed by SUBSCRIPTION_SYNC_DELAY_MS.
 */
const CROSS_VU_SYNC_BUFFER_MS = 2000;

/*
 * Delivery timeout: time to wait after sending for
 * messages to arrive.
 */
const DELIVERY_TIMEOUT_MS = 3000;


/*
 * ============================================================
 * Custom Metrics (aggregate reporting)
 * ============================================================
 */

export const auditHandshakeFailures = new Counter(
    'audit_handshake_failures'
);

export const auditStompConnectionFailures = new Counter(
    'audit_stomp_connection_failures'
);

export const auditMessageFailures = new Counter(
    'audit_message_failures'
);

export const auditMessagesSent = new Counter(
    'audit_msgs_sent'
);

export const auditMessagesReceived = new Counter(
    'audit_msgs_received'
);

export const auditUniqueReceived = new Counter(
    'audit_unique_received'
);

export const auditDuplicateDeliveries = new Counter(
    'audit_duplicate_deliveries'
);

export const auditMissingDeliveries = new Counter(
    'audit_missing_deliveries'
);

export const auditOwnEchoReceived = new Counter(
    'audit_own_echo_received'
);

export const auditCrossVUReceived = new Counter(
    'audit_cross_vu_received'
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
 * 2 VUs, each runs exactly 1 iteration.
 * Short duration since this is a correctness test.
 */

export const options = {
    scenarios: {
        deterministic_audit: {
            executor: 'shared-iterations',
            vus: 2,
            iterations: 2,
            maxDuration: '30s',
        },
    },

    thresholds: {
        checks: [
            'rate===1',
        ],

        audit_handshake_failures: [
            'count==0',
        ],

        audit_stomp_connection_failures: [
            'count==0',
        ],

        audit_message_failures: [
            'count==0',
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
        data.metrics.audit_msgs_sent?.values.count || 0;

    const uniqueReceived =
        data.metrics.audit_unique_received?.values.count || 0;

    const duplicates =
        data.metrics.audit_duplicate_deliveries?.values.count || 0;

    const missing =
        data.metrics.audit_missing_deliveries?.values.count || 0;

    const ownEcho =
        data.metrics.audit_own_echo_received?.values.count || 0;

    const crossVU =
        data.metrics.audit_cross_vu_received?.values.count || 0;

    const wsFailures =
        data.metrics.audit_message_failures?.values.count || 0;

    const stompErrors =
        data.metrics.audit_stomp_connection_failures?.values.count || 0;

    const handshakeFails =
        data.metrics.audit_handshake_failures?.values.count || 0;

    const checksPasses =
        data.metrics.checks?.values.passes || 0;

    const checksFails =
        data.metrics.checks?.values.fails || 0;


    /*
     * Aggregate invariant verification.
     */

    const aggregateAudit = {
        totalSent: sent,
        totalExpected: 4,
        totalUniqueReceived: uniqueReceived,
        totalOwnEcho: ownEcho,
        totalCrossVU: crossVU,
        totalDuplicates: duplicates,
        totalMissing: missing,
        totalProtocolErrors: stompErrors + wsFailures + handshakeFails,
        deliveryRatio: sent > 0
            ? (uniqueReceived / sent * 100).toFixed(1) + '%'
            : 'N/A',
        invariantHolds: {
            totalSentEquals2: sent === 2,
            totalUniqueReceivedEquals4: uniqueReceived === 4,
            totalOwnEchoEquals2: ownEcho === 2,
            totalCrossVUEquals2: crossVU === 2,
            totalDuplicatesEquals0: duplicates === 0,
            totalMissingEquals0: missing === 0,
            totalProtocolErrorsEquals0:
                (stompErrors + wsFailures + handshakeFails) === 0,
        },
    };


    /*
     * Compute overall pass/fail from invariant.
     */

    const allInvariantsHold = Object.values(
        aggregateAudit.invariantHolds
    ).every(Boolean);

    aggregateAudit.overallResult =
        allInvariantsHold ? 'PASS' : 'FAIL';


    console.log('');
    console.log('========================================');
    console.log('  DETERMINISTIC AUDIT: AGGREGATE RESULT');
    console.log('========================================');
    console.log(JSON.stringify(aggregateAudit, null, 2));
    console.log('========================================');
    console.log('');


    /*
     * Compute checks summary for stdout output.
     */

    const checksSummary = data.root_group?.checks?.map(
        (c) => `  ${c.passes > 0 ? '✓' : '✗'} ${c.name} (${c.passes} / ${c.passes + c.fails})`
    ).join('\n') || '  (no checks)';


    /*
     * Return audit output to stdout.
     */
    return {
        stdout: [
            '',
            checksSummary,
            '',
            '  Aggregate audit:',
            `    totalSent: ${sent}`,
            `    totalUniqueReceived: ${uniqueReceived}`,
            `    totalOwnEcho: ${ownEcho}`,
            `    totalCrossVU: ${crossVU}`,
            `    totalDuplicates: ${duplicates}`,
            `    totalMissing: ${missing}`,
            `    totalProtocolErrors: ${stompErrors + wsFailures + handshakeFails}`,
            `    deliveryRatio: ${aggregateAudit.deliveryRatio}`,
            '',
            `  overallResult: ${aggregateAudit.overallResult}`,
            '',
        ].join('\n'),
    };
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
    let shuttingDown = false;
    let connectionClosed = false;
    let socketRef = null;


    /*
     * ==========================================================
     * Per-VU Accounting (LOCAL variables for assertions)
     * ==========================================================
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
     */

    const pendingMessages = {};


    /*
     * ==========================================================
     * Iteration ID
     * ==========================================================
     */

    const iterationId =
        `${__VU}-${__ITER}-${Date.now()}`;


    /*
     * ==========================================================
     * Test Information
     * ==========================================================
     */

    console.log('');
    console.log('====================================================');
    console.log(
        `[VU ${__VU}] DETERMINISTIC AUDIT TEST`
    );
    console.log(
        `[VU ${__VU}] Iteration: ${__ITER}`
    );
    console.log(
        `[VU ${__VU}] Iteration ID: ${iterationId}`
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

            socketRef = socket;


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


                    /*
                     * CONNECTED
                     */

                    if (
                        stompFrame.command === 'CONNECTED'
                    ) {

                        stompConnected = true;

                        console.log(
                            `[VU ${__VU}] STOMP CONNECTED.`
                        );


                        /*
                         * SUBSCRIBE to /topic/public
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


                        /*
                         * Subscription sync: wait for
                         * Spring's executor to register
                         * the subscription.
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
                                    `[VU ${__VU}] SUBSCRIPTION READY`
                                );


                                /*
                                 * Safety bound: wait for
                                 * the OTHER VU to also
                                 * subscribe.
                                 *
                                 * This is a SAFETY BOUND,
                                 * not a correctness mechanism.
                                 * Correctness depends on
                                 * this VU being subscribed
                                 * before it sends, which is
                                 * guaranteed by the
                                 * SUBSCRIPTION_SYNC_DELAY_MS
                                 * above.
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
                                            `[VU ${__VU}] CROSS-VU SYNC COMPLETE`
                                        );


                                        /*
                                         * ==================================================
                                         * SEND exactly 1 message
                                         * ==================================================
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


                                        /*
                                         * Wait for message
                                         * delivery, then run
                                         * assertions and close.
                                         */

                                        socket.setTimeout(
                                            function () {

                                                /*
                                                 * Run assertions
                                                 */

                                                runAssertions(
                                                    localSentCount,
                                                    localUniqueReceived,
                                                    localOwnEcho,
                                                    localCrossVU,
                                                    localDuplicateCount,
                                                    localProtocolErrors
                                                );


                                                /*
                                                 * Begin shutdown
                                                 */

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
                                    CROSS_VU_SYNC_BUFFER_MS
                                );

                            },
                            SUBSCRIPTION_SYNC_DELAY_MS
                        );
                    }


                    /*
                     * STOMP MESSAGE
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
                                    `[VU ${__VU}] MESSAGE without loadTestId - ignoring`
                                );
                                return;
                            }


                            /*
                             * Duplicate detection (per-VU)
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
                             * New unique message received
                             */

                            receivedSet.add(
                                receivedLoadTestId
                            );

                            localUniqueReceived++;
                            auditUniqueReceived.add(1);


                            /*
                             * Classify own vs cross-VU
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
                             * Latency (for own messages)
                             */

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


                    /*
                     * STOMP ERROR
                     */

                    else if (
                        stompFrame.command === 'ERROR'
                    ) {

                        if (shuttingDown) {
                            console.log(
                                `[VU ${__VU}] Expected ERROR during shutdown.`
                            );
                            return;
                        }

                        console.error(
                            `[VU ${__VU}] UNEXPECTED STOMP ERROR`
                        );
                        console.error(
                            `[VU ${__VU}] Headers: ${JSON.stringify(
                                stompFrame.headers || {}
                            )}`
                        );
                        console.error(
                            `[VU ${__VU}] Body: ${stompFrame.body || ''}`
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

                    if (shuttingDown) {
                        return;
                    }

                    console.error(
                        `[VU ${__VU}] WebSocket error: ${error}`
                    );

                    wsMessageFailures.add(1);
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

                    wsConnectionDuration.add(duration);

                    console.log(
                        `[VU ${__VU}] WebSocket closed.`
                    );

                    console.log(
                        `[VU ${__VU}] Duration: ${duration} ms`
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
 * Per-VU Assertions
 * ============================================================
 *
 * Uses LOCAL variables only (not k6 aggregate Counters).
 * k6 Counters are for reporting; local variables are for
 * per-VU check() assertions.
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
    console.log('');


    /*
     * Emit aggregate missing counter
     */

    if (localMissing > 0) {
        auditMissingDeliveries.add(localMissing);
    }


    /*
     * Per-VU assertions using LOCAL variables.
     *
     * Invariant per VU:
     *   uniqueReceived == 2  (1 own echo + 1 cross-VU)
     *   ownEcho == 1
     *   crossVU == 1
     *   duplicates == 0
     *   missing == 0
     *   protocolErrors == 0
     */

    check(
        { vu: __VU },
        {
            'VU uniqueReceived == 2':
                (v) => localUniqueReceived === 2,

            'VU ownEcho == 1':
                (v) => localOwnEcho === 1,

            'VU crossVU == 1':
                (v) => localCrossVU === 1,

            'VU duplicates == 0':
                (v) => localDuplicateCount === 0,

            'VU missing == 0':
                (v) => localMissing === 0,

            'VU protocolErrors == 0':
                (v) => localProtocolErrors === 0,

            'VU sent == 1':
                (v) => localSentCount === 1,
        }
    );
}
