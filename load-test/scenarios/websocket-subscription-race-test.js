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
 * Subscription Race Investigation Test
 * ============================================================
 *
 * Purpose: Determine whether the SUBSCRIBE -> SEND race causes
 * message loss due to subscription registration timing.
 *
 * Two experiments:
 *
 * Experiment A (TEST_MODE=sync):
 *   Both VUs complete subscription registration before either
 *   sends. Expected: 4 deliveries (2 messages x 2 subscribers).
 *
 * Experiment B (TEST_MODE=immediate):
 *   SEND fires immediately after SUBSCRIBE with no sync delay.
 *   The race between subscription registration and message
 *   publish is exposed. Expected: some deliveries may be lost.
 *
 * Synchronization:
 *   k6 v2.2.0 does not provide VU-to-VU communication.
 *   Experiment A uses SUBSCRIPTION_SYNC_DELAY_MS + CROSS_VU_SYNC_BUFFER_MS
 *   to ensure both VUs are subscribed before sending.
 *   Experiment B uses 0ms delays to maximize race exposure.
 */


/*
 * ============================================================
 * Configuration
 * ============================================================
 */

const WS_URL = getWebSocketUrl();

const TEST_MODE = __ENV.TEST_MODE || 'sync';

const SUBSCRIPTION_SYNC_DELAY_MS = TEST_MODE === 'sync' ? 100 : 1;
const CROSS_VU_SYNC_BUFFER_MS = TEST_MODE === 'sync' ? 2000 : 1;
const DELIVERY_TIMEOUT_MS = 3000;


/*
 * ============================================================
 * Custom Metrics
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
 */

export const options = {
    scenarios: {
        subscription_race_test: {
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

    const separatorIndex = cleaned.indexOf('\n\n');

    let headerPart;
    let body;

    if (separatorIndex === -1) {
        headerPart = cleaned;
        body = '';
    } else {
        headerPart = cleaned.substring(0, separatorIndex);
        body = cleaned.substring(separatorIndex + 2);
    }

    const lines = headerPart.split('\n');
    const command = lines.shift()?.trim();

    if (!command) {
        return null;
    }

    const headers = {};

    for (const line of lines) {
        const colonIndex = line.indexOf(':');
        if (colonIndex === -1) {
            continue;
        }
        const key = line.substring(0, colonIndex).trim();
        const value = line.substring(colonIndex + 1).trim();
        headers[key] = value;
    }

    return { command, headers, body };
}


/*
 * ============================================================
 * Send Native STOMP Frame
 * ============================================================
 */

function sendStompFrame(socket, frame, description = 'STOMP frame') {
    console.log(`[VU ${__VU}] Sending ${description}.`);
    socket.send(frame);
}


/*
 * ============================================================
 * handleSummary (audit output)
 * ============================================================
 */

export function handleSummary(data) {

    const sent = data.metrics.audit_msgs_sent?.values.count || 0;
    const uniqueReceived = data.metrics.audit_unique_received?.values.count || 0;
    const duplicates = data.metrics.audit_duplicate_deliveries?.values.count || 0;
    const missing = data.metrics.audit_missing_deliveries?.values.count || 0;
    const ownEcho = data.metrics.audit_own_echo_received?.values.count || 0;
    const crossVU = data.metrics.audit_cross_vu_received?.values.count || 0;
    const wsFailures = data.metrics.audit_message_failures?.values.count || 0;
    const stompErrors = data.metrics.audit_stomp_connection_failures?.values.count || 0;
    const handshakeFails = data.metrics.audit_handshake_failures?.values.count || 0;

    const aggregateAudit = {
        testMode: TEST_MODE,
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
    };

    if (TEST_MODE === 'sync') {
        aggregateAudit.invariantHolds = {
            totalSentEquals2: sent === 2,
            totalUniqueReceivedEquals4: uniqueReceived === 4,
            totalOwnEchoEquals2: ownEcho === 2,
            totalCrossVUEquals2: crossVU === 2,
            totalDuplicatesEquals0: duplicates === 0,
            totalMissingEquals0: missing === 0,
        };

        const allInvariantsHold = Object.values(
            aggregateAudit.invariantHolds
        ).every(Boolean);

        aggregateAudit.overallResult =
            allInvariantsHold ? 'PASS' : 'FAIL';
    } else {
        aggregateAudit.raceAnalysis = {
            expectedDeliveries: 4,
            actualDeliveries: uniqueReceived,
            lostDeliveries: Math.max(0, 4 - uniqueReceived),
            raceTriggered: uniqueReceived < 4,
        };

        aggregateAudit.overallResult =
            uniqueReceived >= 4 ? 'NO_RACE_DETECTED' : 'RACE_DETECTED';
    }

    console.log('');
    console.log('========================================');
    console.log('  SUBSCRIPTION RACE TEST: RESULT');
    console.log('  Mode: ' + TEST_MODE);
    console.log('========================================');
    console.log(JSON.stringify(aggregateAudit, null, 2));
    console.log('========================================');
    console.log('');

    return {
        stdout: [
            '',
            '  Test Mode: ' + TEST_MODE,
            '',
            '  Aggregate audit:',
            '    totalSent: ' + sent,
            '    totalUniqueReceived: ' + uniqueReceived,
            '    totalOwnEcho: ' + ownEcho,
            '    totalCrossVU: ' + crossVU,
            '    totalDuplicates: ' + duplicates,
            '    totalMissing: ' + missing,
            '    totalProtocolErrors: ' + (stompErrors + wsFailures + handshakeFails),
            '    deliveryRatio: ' + aggregateAudit.deliveryRatio,
            '',
            '  overallResult: ' + aggregateAudit.overallResult,
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
     * Mutable state object shared between message handler
     * and assertions. Using an object ensures the assertions
     * see the latest values, not stale copies.
     */
    const state = {
        stompConnected: false,
        subscriptionReady: false,
        shuttingDown: false,
        connectionClosed: false,
        socketRef: null,
        receivedSet: new Set(),
        localSentCount: 0,
        localDuplicateCount: 0,
        localUniqueReceived: 0,
        localOwnEcho: 0,
        localCrossVU: 0,
        localProtocolErrors: 0,
        pendingMessages: {},
    };

    const iterationId = __VU + '-' + __ITER + '-' + Date.now();

    console.log('');
    console.log('====================================================');
    console.log('[VU ' + __VU + '] SUBSCRIPTION RACE TEST');
    console.log('[VU ' + __VU + '] Mode: ' + TEST_MODE);
    console.log('[VU ' + __VU + '] Iteration: ' + __ITER);
    console.log('[VU ' + __VU + '] Iteration ID: ' + iterationId);
    console.log('====================================================');
    console.log('');

    const params = {
        headers: {
            'Sec-WebSocket-Protocol': 'v12.stomp',
            'X-Internal-Secret': ENV.INTERNAL_SECRET,
        },
    };

    const response = ws.connect(
        WS_URL,
        params,
        function (socket) {

            state.socketRef = socket;

            socket.on('open', function () {
                console.log('[VU ' + __VU + '] WebSocket opened.');

                const connectFrame = buildStompFrame(
                    'CONNECT',
                    {
                        'accept-version': '1.2',
                        'host': 'localhost',
                        'heart-beat': '10000,10000',
                        'Authorization': 'Bearer ' + ENV.AUTH_TOKEN,
                    }
                );

                sendStompFrame(socket, connectFrame, 'STOMP CONNECT');
            });


            socket.on('message', function (rawData) {

                const stompFrame = parseStompFrame(rawData);
                if (!stompFrame) {
                    return;
                }

                console.log('[VU ' + __VU + '] STOMP command: ' + stompFrame.command);


                /*
                 * CONNECTED
                 */

                if (stompFrame.command === 'CONNECTED') {

                    state.stompConnected = true;
                    console.log('[VU ' + __VU + '] STOMP CONNECTED.');

                    const subscribeFrame = buildStompFrame(
                        'SUBSCRIBE',
                        {
                            id: 'sub-' + __VU + '-' + __ITER,
                            destination: ENV.ENDPOINTS.TOPIC_PUBLIC,
                            ack: 'auto',
                        }
                    );

                    sendStompFrame(socket, subscribeFrame, 'STOMP SUBSCRIBE');

                    /*
                     * Subscription sync: wait for Spring's
                     * clientInboundChannel executor to register.
                     *
                     * Experiment A: wait 100ms
                     * Experiment B: wait 0ms (immediate)
                     */
                    socket.setTimeout(function () {

                        if (state.shuttingDown || state.connectionClosed) {
                            return;
                        }

                        state.subscriptionReady = true;
                        console.log('[VU ' + __VU + '] SUBSCRIPTION READY (mode=' + TEST_MODE + ')');

                        if (TEST_MODE === 'immediate') {
                            doSend(socket, iterationId, state);
                            startDeliveryWait(socket, state);
                            return;
                        }

                        /*
                         * Experiment A only:
                         * Safety bound for the OTHER VU.
                         */
                        socket.setTimeout(function () {

                            if (state.shuttingDown || state.connectionClosed) {
                                return;
                            }

                            console.log('[VU ' + __VU + '] CROSS-VU SYNC COMPLETE');
                            doSend(socket, iterationId, state);
                            startDeliveryWait(socket, state);

                        }, CROSS_VU_SYNC_BUFFER_MS);

                    }, SUBSCRIPTION_SYNC_DELAY_MS);
                }


                /*
                 * STOMP MESSAGE
                 */

                else if (stompFrame.command === 'MESSAGE') {

                    auditMessagesReceived.add(1);
                    const body = stompFrame.body;

                    try {
                        const parsedBody = JSON.parse(body);
                        const receivedLoadTestId = parsedBody.loadTestId;

                        if (!receivedLoadTestId) {
                            console.log('[VU ' + __VU + '] MESSAGE without loadTestId - ignoring');
                            return;
                        }

                        if (state.receivedSet.has(receivedLoadTestId)) {
                            state.localDuplicateCount++;
                            auditDuplicateDeliveries.add(1);
                            console.log('[VU ' + __VU + '] DUPLICATE: ' + receivedLoadTestId);
                            return;
                        }

                        state.receivedSet.add(receivedLoadTestId);
                        state.localUniqueReceived++;
                        auditUniqueReceived.add(1);

                        const isOwnMessage = receivedLoadTestId.indexOf(iterationId + '-') === 0;

                        if (isOwnMessage) {
                            state.localOwnEcho++;
                            auditOwnEchoReceived.add(1);
                            console.log('[VU ' + __VU + '] OWN ECHO: ' + receivedLoadTestId);
                        } else {
                            state.localCrossVU++;
                            auditCrossVUReceived.add(1);
                            console.log('[VU ' + __VU + '] CROSS-VU: ' + receivedLoadTestId);
                        }

                        const sentTimestamp = state.pendingMessages[receivedLoadTestId];
                        if (sentTimestamp !== undefined) {
                            const latency = Date.now() - sentTimestamp;
                            if (latency >= 0) {
                                wsMessageLatency.add(latency);
                            }
                            delete state.pendingMessages[receivedLoadTestId];
                        }

                    } catch (error) {
                        console.error('[VU ' + __VU + '] Parse error: ' + error);
                        auditMessageFailures.add(1);
                        state.localProtocolErrors++;
                    }
                }


                /*
                 * STOMP ERROR
                 */

                else if (stompFrame.command === 'ERROR') {

                    if (state.shuttingDown) {
                        console.log('[VU ' + __VU + '] Expected ERROR during shutdown.');
                        return;
                    }

                    console.error('[VU ' + __VU + '] UNEXPECTED STOMP ERROR');
                    console.error('[VU ' + __VU + '] Headers: ' + JSON.stringify(stompFrame.headers || {}));
                    console.error('[VU ' + __VU + '] Body: ' + (stompFrame.body || ''));

                    auditStompConnectionFailures.add(1);
                    state.localProtocolErrors++;
                    socket.close();
                }
            });


            socket.on('error', function (error) {
                if (state.shuttingDown) {
                    return;
                }
                console.error('[VU ' + __VU + '] WebSocket error: ' + error);
                auditMessageFailures.add(1);
                state.localProtocolErrors++;
            });


            socket.on('close', function () {
                state.connectionClosed = true;
                const duration = Date.now() - startTime;
                wsConnectionDuration.add(duration);
                console.log('[VU ' + __VU + '] WebSocket closed.');
                console.log('[VU ' + __VU + '] Duration: ' + duration + ' ms');

                if (!state.stompConnected) {
                    auditStompConnectionFailures.add(1);
                    state.localProtocolErrors++;
                }
            });
        }
    );

    const handshakeSuccessful = response && response.status === 101;

    check(response, {
        'WebSocket handshake status is 101': (r) => r && r.status === 101,
    });

    if (!handshakeSuccessful) {
        auditHandshakeFailures.add(1);
    }
}


/*
 * ============================================================
 * SEND helper
 * ============================================================
 */

function doSend(socket, iterationId, state) {

    const chatMessage = generateChatMessage(__VU);
    chatMessage.loadTestId = iterationId + '-msg-1';

    const sendTimestamp = Date.now();
    state.pendingMessages[chatMessage.loadTestId] = sendTimestamp;

    const messageBody = JSON.stringify(chatMessage);

    const sendFrame = buildStompFrame(
        'SEND',
        {
            destination: ENV.ENDPOINTS.SEND_MESSAGE,
            'content-type': 'application/json',
        },
        messageBody
    );

    sendStompFrame(socket, sendFrame, 'STOMP SEND (1 message)');

    state.localSentCount++;
    auditMessagesSent.add(1);

    console.log('[VU ' + __VU + '] SENT msgId=' + chatMessage.loadTestId);
}


/*
 * ============================================================
 * Delivery wait + assertions
 * ============================================================
 */

function startDeliveryWait(socket, state) {

    socket.setTimeout(function () {

        /*
         * Run assertions using the mutable state object.
         * state.localUniqueReceived etc. reflect the latest
         * values because the MESSAGE handler updates them
         * directly on the state object.
         */

        runAssertions(state);

        state.shuttingDown = true;

        const disconnectFrame = buildStompFrame('DISCONNECT', {});
        sendStompFrame(socket, disconnectFrame, 'STOMP DISCONNECT');

        socket.setTimeout(function () {
            socket.close();
        }, 500);

    }, DELIVERY_TIMEOUT_MS);
}


/*
 * ============================================================
 * Per-VU Assertions
 * ============================================================
 */

function runAssertions(state) {

    const localMissing = Math.max(0, state.localSentCount - state.localUniqueReceived);

    console.log('');
    console.log('[VU ' + __VU + '] -- ACCOUNTING SUMMARY --');
    console.log('[VU ' + __VU + ']   mode: ' + TEST_MODE);
    console.log('[VU ' + __VU + ']   sent: ' + state.localSentCount);
    console.log('[VU ' + __VU + ']   uniqueReceived: ' + state.localUniqueReceived);
    console.log('[VU ' + __VU + ']   ownEcho: ' + state.localOwnEcho);
    console.log('[VU ' + __VU + ']   crossVU: ' + state.localCrossVU);
    console.log('[VU ' + __VU + ']   duplicates: ' + state.localDuplicateCount);
    console.log('[VU ' + __VU + ']   missing: ' + localMissing);
    console.log('[VU ' + __VU + ']   protocolErrors: ' + state.localProtocolErrors);
    console.log('');

    if (localMissing > 0) {
        auditMissingDeliveries.add(localMissing);
    }

    if (TEST_MODE === 'sync') {
        check(
            { vu: __VU },
            {
                'VU uniqueReceived == 2': (v) => state.localUniqueReceived === 2,
                'VU ownEcho == 1': (v) => state.localOwnEcho === 1,
                'VU crossVU == 1': (v) => state.localCrossVU === 1,
                'VU duplicates == 0': (v) => state.localDuplicateCount === 0,
                'VU missing == 0': (v) => localMissing === 0,
                'VU protocolErrors == 0': (v) => state.localProtocolErrors === 0,
                'VU sent == 1': (v) => state.localSentCount === 1,
            }
        );
    } else {
        check(
            { vu: __VU },
            {
                'VU protocolErrors == 0': (v) => state.localProtocolErrors === 0,
                'VU duplicates == 0': (v) => state.localDuplicateCount === 0,
                'VU sent == 1': (v) => state.localSentCount === 1,
                'VU received >= 1': (v) => state.localUniqueReceived >= 1,
            }
        );
    }
}
