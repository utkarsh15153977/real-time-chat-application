/**
 * Global Configuration for k6 Load Testing Suite
 * Target System: Real-time Chat Microservices Architecture
 */
export const ENV = {
    // Base Gateway URLs
    HTTP_BASE_URL: __ENV.HTTP_BASE_URL || 'http://localhost:8080',
    WS_BASE_URL: __ENV.WS_BASE_URL || 'ws://localhost:8080',

    // Security Token Fallback
    AUTH_TOKEN: __ENV.AUTH_TOKEN || 'eyJhbGciOiJIUzM4NCJ9.eyJzdWIiOiJzaW5naC51dGthcnNoLmRldmVsb3BlckBnbWFpbC5jb20iLCJ1c2VySWQiOjUsIm5hbWUiOiJVdGthcnNoIFNpbmdoIiwiaWF0IjoxNzg4ODcyNjM2LCJleHAiOjE3ODg5NTkwMzZ9.q2Q0ublfJPDPV5R__1GaaVtAEE2jnElqzpf1FbXKc2gwKhS1Su_Nc201SB6cdVYh',

    // Endpoint Paths
    ENDPOINTS: {
        HEALTH: '/actuator/health',
        WEBSOCKET: '/ws',
        SEND_MESSAGE: '/app/chat.sendMessage',
        TOPIC_PUBLIC: '/topic/public',
    },



    // Security Credentials
    INTERNAL_SECURITY_SECRET: __ENV.INTERNAL_SECURITY_SECRET || 'super-secret-internal-key-blink-2026',

    // Load Parameters
    TEST_PARAMS: {
        TARGET_VUS: parseInt(__ENV.TARGET_VUS || '10', 10),
        RAMP_DURATION: __ENV.RAMP_DURATION || '10s',
        SOAK_DURATION: __ENV.SOAK_DURATION || '30s',
        MESSAGE_INTERVAL_MS: parseInt(__ENV.MSG_INTERVAL || '1000', 10),
    },

    // Performance SLA Thresholds
    THRESHOLDS: {
        WS_LATENCY_P95_MS: 300,    // 95% of broadcasted msgs delivered within 300ms
        HTTP_LATENCY_P95_MS: 200,  // 95% of HTTP requests complete within 200ms
        MAX_FAILURE_RATE: 0.01,    // Max 1% error tolerance
    },
};

/**
 * Utility Helper: Formats a STOMP 1.1/1.2 frame buffer
 */
export function buildStompFrame(command, headers = {}, body = '') {
    const NULL_BYTE = '\u0000';
    let frame = `${command}\n`;
    for (const [key, value] of Object.entries(headers)) {
        frame += `${key}:${value}\n`;
    }
    frame += `\n${body}${NULL_BYTE}`;
    return frame;
}

/**
 * Utility Helper: Generates a dynamic chat payload for a given VU
 */
export function generateChatMessage(vuId) {
    const timestamp = Date.now();
    return JSON.stringify({
        sender: `vu-user-${vuId}`,
        content: `Load test pulse from VU ${vuId} at ${timestamp}`,
        timestamp: timestamp,
    });
}

/**
 * Utility Helper: Generates a SockJS-compliant WebSocket URL
 * Format: ws://localhost:8080/ws/<serverId>/<sessionId>/websocket
 */
export function getSockJsWsUrl() {
    const serverId = Math.floor(Math.random() * 1000).toString().padStart(3, '0');
    const sessionId = Math.random().toString(36).substring(2, 10);

    const baseUrl = ENV.WS_BASE_URL.replace(/\/+$/, '');
    const wsEndpoint = ENV.ENDPOINTS.WEBSOCKET.replace(/^\/+|\/+$/g, '');

    return `${baseUrl}/${wsEndpoint}/${serverId}/${sessionId}/websocket`;
}

export default function () {
    console.log(JSON.stringify(ENV));
}