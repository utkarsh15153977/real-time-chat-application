/**
 * Global Configuration for k6 Load Testing Suite
 * Target System: Real-time Chat Microservices Architecture
 */

function parseIntEnv(name, defaultValue) {
    const value = __ENV[name];

    if (value === undefined || value === '') {
        return defaultValue;
    }

    const parsed = Number.parseInt(value, 10);

    if (Number.isNaN(parsed)) {
        throw new Error(
            `${name} must be a valid integer. Received: ${value}`
        );
    }

    return parsed;
}

function parseFloatEnv(name, defaultValue) {
    const value = __ENV[name];

    if (value === undefined || value === '') {
        return defaultValue;
    }

    const parsed = Number.parseFloat(value);

    if (Number.isNaN(parsed)) {
        throw new Error(
            `${name} must be a valid number. Received: ${value}`
        );
    }

    return parsed;
}

function normalizeBaseUrl(url) {
    return url.replace(/\/+$/, '');
}

function requireAuthToken() {
    const token = __ENV.AUTH_TOKEN;

    if (!token) {
        throw new Error(
            'AUTH_TOKEN is required. Set it with: $env:AUTH_TOKEN="YOUR_TEST_JWT"'
        );
    }

    return token;
}

export const ENV = {
    // -------------------------------------------------------------------------
    // Base URLs
    // -------------------------------------------------------------------------

    HTTP_BASE_URL: normalizeBaseUrl(
        __ENV.HTTP_BASE_URL || 'http://localhost:8081'
    ),

    WS_BASE_URL: normalizeBaseUrl(
        __ENV.WS_BASE_URL || 'ws://localhost:8083'
    ),

    // -------------------------------------------------------------------------
    // Authentication
    // -------------------------------------------------------------------------

    AUTH_TOKEN: requireAuthToken(),

    // -------------------------------------------------------------------------
    // Endpoint Paths
    // -------------------------------------------------------------------------

    ENDPOINTS: {
        HEALTH: '/actuator/health',

        // Native WebSocket STOMP endpoint.
        // Used by k6 load tests.
        WEBSOCKET: '/ws',

        // SockJS endpoint.
        // Used by browser/client applications that require SockJS.
        WEBSOCKET_SOCKJS: '/ws-sockjs',

        // STOMP application destination.
        SEND_MESSAGE: '/app/chat.sendMessage',

        // STOMP subscription destination.
        TOPIC_PUBLIC: '/topic/public',
    },

    // -------------------------------------------------------------------------
    // Load Parameters
    // -------------------------------------------------------------------------

    TEST_PARAMS: {
        TARGET_VUS: parseIntEnv('TARGET_VUS', 10),

        RAMP_DURATION: __ENV.RAMP_DURATION || '10s',

        SOAK_DURATION: __ENV.SOAK_DURATION || '30s',

        RAMP_DOWN_DURATION:
            __ENV.RAMP_DOWN_DURATION || '5s',

        // Time between messages from each connected VU.
        MESSAGE_INTERVAL_MS:
            parseIntEnv('MSG_INTERVAL', 1000),

        // How long an individual WebSocket connection remains open.
        CONNECTION_DURATION_MS:
            parseIntEnv(
                'CONNECTION_DURATION_MS',
                35000
            ),

        // How long to wait for STOMP CONNECTED.
        CONNECT_TIMEOUT_MS:
            parseIntEnv(
                'CONNECT_TIMEOUT_MS',
                5000
            ),
    },

    // -------------------------------------------------------------------------
    // Performance SLA Thresholds
    // -------------------------------------------------------------------------

    THRESHOLDS: {
        // 95% of successfully delivered messages should arrive
        // within this amount of time.
        WS_LATENCY_P95_MS:
            parseFloatEnv(
                'WS_LATENCY_P95_MS',
                300
            ),

        // Maximum acceptable message failure rate.
        MAX_FAILURE_RATE:
            parseFloatEnv(
                'MAX_FAILURE_RATE',
                0.01
            ),
    },
};


/**
 * Formats a STOMP 1.1/1.2 frame.
 *
 * STOMP frame format:
 *
 * COMMAND
 * header:value
 *
 * body\0
 */
export function buildStompFrame(
    command,
    headers = {},
    body = ''
) {
    const NULL_BYTE = '\u0000';

    let frame = `${command}\n`;

    for (const [key, value] of Object.entries(headers)) {
        frame += `${key}:${value}\n`;
    }

    frame += `\n${body}${NULL_BYTE}`;

    return frame;
}


/**
 * Generates a unique message payload for a VU.
 *
 * Field names match ChatMessageRequestDTO exactly:
 *   senderId, recipientId, chatRoomId, content, messageType, loadTestId
 */
export function generateChatMessage(vuId) {
    const timestamp = Date.now();

    const loadTestId =
        `k6-vu-${vuId}-${timestamp}-${Math.random()
            .toString(36)
            .slice(2, 10)}`;

    const senderId = String(vuId);
    const recipientId = String(vuId + 1000);
    const chatRoomId = `${senderId}-${recipientId}`;

    return {
        senderId,
        recipientId,
        chatRoomId,
        content: `Load test message ${loadTestId}`,
        messageType: 'TEXT',
        loadTestId,
    };
}


/**
 * Returns the native WebSocket STOMP URL.
 *
 * This is the endpoint configured with:
 *
 * registry.addEndpoint("/ws")
 *
 * Example:
 *
 * ws://localhost:8080/ws
 */
export function getWebSocketUrl() {
    return `${ENV.WS_BASE_URL}${ENV.ENDPOINTS.WEBSOCKET}`;
}


/**
 * Generates the SockJS WebSocket transport URL.
 *
 * This is kept for browser/SockJS-specific testing.
 *
 * Example:
 *
 * ws://localhost:8080/ws-sockjs/123/abc12345/websocket
 */
export function getSockJsWsUrl() {
    const serverId =
        Math.floor(Math.random() * 1000)
            .toString()
            .padStart(3, '0');

    const sessionId =
        Math.random()
            .toString(36)
            .substring(2, 10);

    const wsEndpoint =
        ENV.ENDPOINTS.WEBSOCKET_SOCKJS
            .replace(/^\/+|\/+$/g, '');

    return `${ENV.WS_BASE_URL}/${wsEndpoint}/${serverId}/${sessionId}/websocket`;
}


export default ENV;