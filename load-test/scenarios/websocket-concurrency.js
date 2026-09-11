import ws from 'k6/ws';
import { check, sleep } from 'k6';
import { Counter, Trend } from 'k6/metrics';

import {
    ENV,
    buildStompFrame,
    generateChatMessage,
} from '../config/test-env.js';


/*
 * ============================================================
 * WebSocket Configuration
 * ============================================================
 */

const WS_URL = 'ws://localhost:8083/ws';


/*
 * ============================================================
 * Custom Metrics
 * ============================================================
 */

export const wsHandshakeFailures = new Counter(
    'ws_handshake_failures'
);

export const stompConnectionFailures = new Counter(
    'stomp_connection_failures'
);

export const wsMessageFailures = new Counter(
    'ws_message_failures'
);

export const wsMessagesSent = new Counter(
    'ws_msgs_sent'
);

export const wsMessagesReceived = new Counter(
    'ws_msgs_received'
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

        ws_handshake_failures: [
            'count==0',
        ],

        stomp_connection_failures: [
            'count==0',
        ],

        ws_message_failures: [
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
 *
 * Native WebSocket + STOMP:
 *
 *     WebSocket
 *          ↓
 *     Raw STOMP frame
 *
 * Do NOT JSON.stringify() the STOMP frame.
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
     * STOMP frame structure:
     *
     * COMMAND
     * header:value
     *
     * body
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

    const lines = headerPart.split('\n');

    const command = lines.shift()?.trim();

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
 * Main Test
 * ============================================================
 */

export default function () {

    const startTime = Date.now();

    /*
     * STOMP connection state.
     */
    let stompConnected = false;

    /*
     * Tracks whether at least one message
     * was successfully sent.
     */
    let messageSent = false;

    /*
     * True once intentional shutdown begins.
     *
     * This prevents periodic SEND operations
     * and ignores expected shutdown ERROR frames.
     */
    let shuttingDown = false;

    /*
     * True when the WebSocket actually closes.
     */
    let connectionClosed = false;

    /*
     * Unique ID for this VU iteration.
     */
    const loadTestId =
        `${__VU}-${__ITER}-${Date.now()}`;

    /*
     * Directly connect to the local
     * message-service WebSocket endpoint.
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
        `[VU ${__VU}] Load Test ID: ${loadTestId}`
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
     *
     * Spring STOMP over WebSocket negotiates
     * the STOMP sub-protocol through:
     *
     *     Sec-WebSocket-Protocol
     *
     * v12.stomp = STOMP 1.2
     */

    const params = {
        headers: {
            'Sec-WebSocket-Protocol': 'v12.stomp',
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
             * WebSocket OPEN
             * ==================================================
             */

            socket.on(
                'open',
                function () {

                    console.log(
                        `[VU ${__VU}] WebSocket opened.`
                    );

                    /*
                     * Never print the actual JWT.
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
             * WebSocket MESSAGE
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
                     * STOMP CONNECTED
                     * ==================================================
                     */

                    if (
                        stompFrame.command === 'CONNECTED'
                    ) {

                        stompConnected = true;

                        console.log(
                            `[VU ${__VU}] STOMP CONNECTED successfully.`
                        );


                        /*
                         * Log negotiated STOMP version only.
                         * Never log authentication headers.
                         */

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


                        /*
                         * ==================================================
                         * First Chat Message
                         * ==================================================
                         */

                        const chatMessage =
                            generateChatMessage(
                                __VU
                            );

                        chatMessage.loadTestId =
                            loadTestId;

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
                            'STOMP SEND'
                        );

                        wsMessagesSent.add(1);

                        messageSent = true;


                        /*
                         * ==================================================
                         * Periodic Messages
                         * ==================================================
                         */

                        socket.setInterval(
                            function () {

                                /*
                                 * Do not send anything after
                                 * intentional shutdown begins.
                                 */
                                if (
                                    !stompConnected ||
                                    shuttingDown ||
                                    connectionClosed
                                ) {
                                    return;
                                }

                                const message =
                                    generateChatMessage(
                                        __VU
                                    );

                                message.loadTestId =
                                    loadTestId;

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

                                wsMessagesSent.add(1);

                                messageSent = true;

                            },
                            ENV.TEST_PARAMS.MESSAGE_INTERVAL_MS
                        );


                        /*
                         * ==================================================
                         * Connection Timeout
                         * ==================================================
                         */

                        socket.setTimeout(
                            function () {

                                /*
                                 * Prevent duplicate shutdown.
                                 */
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
                                 * ------------------------------------------------
                                 * Begin intentional shutdown.
                                 * ------------------------------------------------
                                 *
                                 * Set this BEFORE DISCONNECT so that:
                                 *
                                 * 1. Periodic SEND stops.
                                 * 2. Expected STOMP ERROR from a closing
                                 *    session is ignored.
                                 */

                                shuttingDown = true;


                                /*
                                 * Send STOMP DISCONNECT first.
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
                                 * Give the DISCONNECT frame a small
                                 * amount of time to leave the socket.
                                 *
                                 * Then force-close the WebSocket.
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

                        wsMessagesReceived.add(1);

                        const body =
                            stompFrame.body;

                        try {

                            const parsedBody =
                                JSON.parse(body);


                            /*
                             * Only calculate latency for messages
                             * generated by this load-test iteration.
                             */

                            if (
                                parsedBody.loadTestId ===
                                loadTestId
                            ) {

                                const sentTimestamp =
                                    parsedBody.timestamp;

                                if (sentTimestamp) {

                                    const sentTime =
                                        typeof sentTimestamp === 'number'
                                            ? sentTimestamp
                                            : new Date(
                                                sentTimestamp
                                            ).getTime();

                                    const latency =
                                        Date.now() - sentTime;

                                    if (latency >= 0) {

                                        wsMessageLatency.add(
                                            latency
                                        );

                                        console.log(
                                            `[VU ${__VU}] Message latency: ${latency} ms`
                                        );
                                    }
                                }


                                /*
                                 * Verify that the received message
                                 * belongs to this test iteration.
                                 */

                                check(
                                    parsedBody,
                                    {
                                        'message has loadTestId':
                                            (msg) =>
                                                msg.loadTestId ===
                                                loadTestId,
                                    }
                                );
                            }

                        } catch (error) {

                            console.error(
                                `[VU ${__VU}] Failed to parse STOMP MESSAGE body: ${error}`
                            );

                            wsMessageFailures.add(1);
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
                         * During intentional shutdown,
                         * Spring may send:
                         *
                         *     message: Session closed.
                         *
                         * This is expected and should NOT
                         * count as a STOMP connection failure.
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
                         * Do NOT print Authorization headers.
                         */

                        console.error(
                            `[VU ${__VU}] STOMP ERROR headers: ${JSON.stringify(
                                stompFrame.headers || {}
                            )}`
                        );

                        console.error(
                            `[VU ${__VU}] STOMP ERROR body: ${stompFrame.body || ''}`
                        );


                        /*
                         * Count only unexpected STOMP errors.
                         */

                        stompConnectionFailures.add(1);

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
                     * Ignore errors caused by
                     * intentional shutdown.
                     */

                    if (shuttingDown) {
                        return;
                    }

                    console.error(
                        `[VU ${__VU}] WebSocket error: ${error}`
                    );

                    wsMessageFailures.add(1);
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
                     * If the WebSocket closed before
                     * STOMP CONNECTED was received,
                     * treat it as a STOMP connection failure.
                     */

                    if (!stompConnected) {

                        console.error(
                            `[VU ${__VU}] STOMP CONNECT failed before CONNECTED response.`
                        );

                        stompConnectionFailures.add(1);
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

        wsHandshakeFailures.add(1);

        console.error(
            `[VU ${__VU}] WebSocket handshake failed.`
        );


        if (response) {

            console.error(
                `[VU ${__VU}] HTTP status: ${response.status}`
            );


            /*
             * Print response body when available.
             */

            if (response.body) {

                console.error(
                    `[VU ${__VU}] Handshake response body: ${String(response.body).substring(0, 1000)}`
                );
            }


            /*
             * Print response headers.
             *
             * Never print Authorization.
             */

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


        /*
         * The server should normally return
         * the negotiated STOMP protocol here.
         */

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