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
            'X-Internal-Secret': 'super-secret-internal-key-blink-2026',
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
                                 * Never send messages after
                                 * shutdown begins.
                                 */

                                if (
                                    !stompConnected ||
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

                        wsMessagesReceived.add(1);


                        const body =
                            stompFrame.body;


                        try {

                            const parsedBody =
                                JSON.parse(body);


                            console.log(
                                `[VU ${__VU}] MESSAGE body keys: ${Object.keys(parsedBody).join(', ')}`
                            );

                            console.log(
                                `[VU ${__VU}] MESSAGE loadTestId: ${parsedBody.loadTestId}`
                            );


                            /*
                             * ------------------------------------------------
                             * Validate message structure
                             * ------------------------------------------------
                             */

                            const receivedLoadTestId =
                                parsedBody.loadTestId;


                            if (!receivedLoadTestId) {

                                console.log(
                                    `[VU ${__VU}] Received MESSAGE without loadTestId.`
                                );

                                return;
                            }


                            /*
                             * ------------------------------------------------
                             * Check whether this message belongs
                             * to this VU's pending messages.
                             * ------------------------------------------------
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


                                /*
                                 * Record real round-trip latency.
                                 */

                                if (latency >= 0) {

                                    wsMessageLatency.add(
                                        latency
                                    );


                                    console.log(
                                        `[VU ${__VU}] Echoed message matched: ${receivedLoadTestId}`
                                    );

                                    console.log(
                                        `[VU ${__VU}] WebSocket round-trip latency: ${latency} ms`
                                    );
                                }


                                /*
                                 * Remove the message from
                                 * pending messages.
                                 *
                                 * This prevents duplicate MESSAGE
                                 * frames from being counted twice.
                                 */

                                delete pendingMessages[
                                    receivedLoadTestId
                                    ];


                                /*
                                 * Verify the correlation ID.
                                 */

                                check(
                                    parsedBody,
                                    {
                                        'echoed message has matching loadTestId':
                                            (msg) =>
                                                msg.loadTestId ===
                                                receivedLoadTestId,
                                    }
                                );

                            } else {

                                /*
                                 * Message is valid but was not
                                 * generated by this VU/iteration.
                                 *
                                 * This can happen because the VU
                                 * subscribed to a shared topic.
                                 */

                                console.log(
                                    `[VU ${__VU}] Received message from another test/client.`
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
                     * Ignore expected shutdown errors.
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
                     * If WebSocket closes before
                     * STOMP CONNECTED, count it as failure.
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