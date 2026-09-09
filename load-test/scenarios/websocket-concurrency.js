import ws from 'k6/ws';
import { check, sleep } from 'k6';
import { ENV, buildStompFrame, generateChatMessage, getSockJsWsUrl } from '../config/test-env.js';

export const options = {
    stages: [
        { duration: ENV.TEST_PARAMS.RAMP_DURATION, target: ENV.TEST_PARAMS.TARGET_VUS },
        { duration: ENV.TEST_PARAMS.SOAK_DURATION, target: ENV.TEST_PARAMS.TARGET_VUS },
        { duration: '5s', target: 0 },
    ],
    thresholds: {
        checks: [`rate>${1 - ENV.THRESHOLDS.MAX_FAILURE_RATE}`],
    },
};

export default function () {
    const wsUrl = getSockJsWsUrl();

    const params = {
        headers: {
            'Authorization': `Bearer ${ENV.AUTH_TOKEN}`,
        },
    };

    console.log(`[VU ${__VU}] Attempting connection to: ${wsUrl}`);

    const response = ws.connect(wsUrl, params, function (socket) {
        socket.on('open', () => {
            console.log(`[VU ${__VU}] WebSocket Connection Opened! Sending CONNECT frame...`);

            // Send STOMP CONNECT frame inside SockJS JSON array format
            const rawConnect = buildStompFrame('CONNECT', {
                'accept-version': '1.1,1.2',
                'heart-beat': '10000,10000',
            });

            // SockJS requires payload wrapped in JSON array string
            socket.send(JSON.stringify([rawConnect]));
        });

        socket.on('message', (rawData) => {
            // Ignore SockJS system frames
            if (rawData === 'o' || rawData === 'h') return;

            let payload = rawData;

            // Extract STOMP message from SockJS array prefix e.g., a["CONNECTED\n..."]
            if (rawData.startsWith('a')) {
                try {
                    const parsed = JSON.parse(rawData.substring(1));
                    payload = parsed[0] || '';
                } catch (e) {
                    console.error(`[VU ${__VU}] Failed to parse SockJS frame: ${rawData}`);
                    return;
                }
            }

            console.log(`[VU ${__VU}] Received payload: ${payload.trim()}`);

            if (payload.startsWith('CONNECTED')) {
                console.log(`[VU ${__VU}] STOMP Connection Established!`);

                // 1. Subscribe to public topic
                const rawSub = buildStompFrame('SUBSCRIBE', {
                    id: 'sub-0',
                    destination: ENV.ENDPOINTS.TOPIC_PUBLIC,
                });
                socket.send(JSON.stringify([rawSub]));

                // 2. Send test chat message
                const msgPayload = generateChatMessage(__VU);
                const rawSend = buildStompFrame('SEND', {
                    destination: ENV.ENDPOINTS.SEND_MESSAGE,
                    'content-type': 'application/json',
                }, msgPayload);

                socket.send(JSON.stringify([rawSend]));
            }
        });

        socket.on('error', (e) => {
            console.error(`[VU ${__VU}] WebSocket Error: ${e.error()}`);
        });

        socket.setTimeout(() => {
            console.log(`[VU ${__VU}] Closing connection after test duration`);
            socket.close();
        }, 5000);
    });

    // Capture HTTP Handshake verification
    if (!response || response.status !== 101) {
        console.error(`[HANDSHAKE FAILED] Status: ${response ? response.status : 'No Response'} | URL: ${wsUrl}`);
        if (response && response.body) {
            console.error(`Response Body: ${response.body}`);
        }
    }

    check(response, {
        'WebSocket Handshake HTTP 101': (r) => r && r.status === 101,
    });

    sleep(1);
}