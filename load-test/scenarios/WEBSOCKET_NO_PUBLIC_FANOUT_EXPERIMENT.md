# WebSocket No-Public-Fanout Experiment

## 1. Original Test Behavior

The original `websocket-concurrency.js` test:
- All VUs subscribe to `/topic/public`
- When any VU sends a message, it's broadcast to ALL subscribed VUs (N² fan-out)
- Each VU receives messages from ALL other VUs
- Creates exponential message load as VUs increase

**Example with 35 VUs:**
- VU 1 sends 1 message → 35 VUs receive it (including VU 1)
- VU 2 sends 1 message → 35 VUs receive it
- Total: 35 messages sent → 35 × 35 = 1,225 messages received
- **N² fan-out: 35² = 1,225**

## 2. New Benchmark Behavior

The `websocket-no-public-fanout.js` test:
- Each VU subscribes ONLY to its room-specific topic: `/topic/room.{chatRoomId}`
- When a VU sends a message, it's broadcast ONLY to subscribers of that specific room
- Since each VU has a unique room, only that VU receives its own messages
- **No fan-out: 35 messages sent → 35 messages received**

**Example with 35 VUs:**
- VU 1 sends 1 message → only VU 1 receives it (on `/topic/room.1-1001`)
- VU 2 sends 1 message → only VU 2 receives it (on `/topic/room.2-1002`)
- Total: 35 messages sent → 35 messages received
- **No fan-out: 1:1 ratio**

## 3. What Was Removed from the Benchmark

**Removed:**
- Subscription to `/topic/public` destination
- N² message fan-out amplification
- Cross-VU message reception

**Preserved:**
- Same WebSocket endpoint (`/ws`)
- Same authentication mechanism (JWT via STOMP CONNECT)
- Same SEND destination (`/app/chat.sendMessage`)
- Same message interval (1000ms default)
- Same connection duration (35000ms default)
- Same loadTestId correlation and latency calculation
- Same failure metrics and thresholds
- Same ramp-up/ramp-down behavior

## 4. What Was Intentionally NOT Changed

- **Production application code**: No changes to Java backend
- **WebSocketConfig.java**: Broker configuration unchanged
- **ChatWebSocketController.java**: Message handling unchanged
- **MessageServiceImpl.java**: Database operations unchanged
- **InboxServiceImpl.java**: Inbox notifications unchanged
- **application.properties**: Configuration unchanged
- **docker-compose.yml**: Deployment unchanged
- **Database schema**: Table structure unchanged
- **Redis configuration**: Cache configuration unchanged
- **Kafka configuration**: Messaging configuration unchanged
- **Original k6 test**: `websocket-concurrency.js` remains unchanged

## 5. Server-Side Destination Used for Latency Correlation

**Destination:** `/topic/room.{chatRoomId}`

**Format:** `/topic/room.{senderId}-{recipientId}`

**Example:** `/topic/room.1-1001` for VU 1

**Why this works:**
1. Server already broadcasts to room-specific topics in `ChatWebSocketController.java`:
   ```java
   String roomTopic = "/topic/room." + requestDTO.getChatRoomId();
   messagingTemplate.convertAndSend(roomTopic, responseDTO);
   ```
2. Each VU subscribes to its own unique room topic
3. When VU sends a message, server broadcasts to that room topic
4. Only the sender VU is subscribed to that topic → only it receives the response
5. **Real server round-trip: SEND → Server processing → BROADCAST → RECEIVE**

## 6. Why This Still Represents a Real Server Round Trip

The latency measurement captures:
1. **STOMP SEND** from k6 to server
2. **Server processing**: JWT validation, DB INSERT, Redis check, Kafka publish
3. **STOMP BROADCAST** from server to subscribers
4. **STOMP MESSAGE** received by k6
5. **loadTestId correlation** and latency calculation

**Not bypassed:**
- Database operations (INSERT + SELECT COUNT)
- Redis presence check
- Kafka event publishing
- WebSocket/STOMP protocol overhead
- Server-side message routing

**Only removed:**
- Broadcasting to all N VUs (N² fan-out)
- Receiving messages from other VUs

## 7. How to Run

### 1-VU Validation Test
```powershell
$env:AUTH_TOKEN="<your-jwt-token>"; $env:TARGET_VUS=1; $env:SOAK_DURATION="10s"; k6 run load-test/scenarios/websocket-no-public-fanout.js
```

### 35-VU Controlled Experiment
```powershell
$env:AUTH_TOKEN="<your-jwt-token>"; $env:TARGET_VUS=35; $env:RAMP_DURATION="15s"; $env:SOAK_DURATION="60s"; $env:RAMP_DOWN_DURATION="15s"; $env:CONNECTION_DURATION_MS=60000; k6 run load-test/scenarios/websocket-no-public-fanout.js
```

### Key Metrics to Record
- `ws_message_latency` (avg, median, p90, p95, max)
- `ws_msgs_sent`
- `ws_msgs_received`
- `ws_handshake_failures`
- `stomp_connection_failures`
- `ws_message_failures`
- `checks` rate

## 8. Result That Would Confirm /topic/public Fan-Out as Bottleneck

**If removing /topic/public fan-out causes p95 latency to drop dramatically:**

- **Original 35 VUs:** p95 = 1.63 seconds
- **No-public-fanout 35 VUs:** p95 = tens/hundreds of milliseconds

**Interpretation:** Strong evidence that N² fan-out on `/topic/public` is the primary bottleneck.

**Expected outcome:** Latency should return to roughly the 30-VU baseline (92ms p95) or similar, since we've removed the exponential amplification.

## 9. Result That Would Disprove /topic/public as Sole Bottleneck

**If latency remains around seconds:**

- **Original 35 VUs:** p95 = 1.63 seconds
- **No-public-fanout 35 VUs:** p95 = still seconds

**Interpretation:** `/topic/public` is not the sole bottleneck. Other factors are dominant:
- Inbound channel saturation
- Synchronous database operations
- HikariCP connection pool limits
- Broker/outbound channel executor saturation
- Redis/Kafka overhead
- JVM resource pressure

## 10. Result That Would Indicate Partial Contribution

**If latency improves partially but remains high:**

- **Original 35 VUs:** p95 = 1.63 seconds
- **No-public-fanout 35 VUs:** p95 = hundreds of milliseconds (not tens)

**Interpretation:** Fan-out is one contributor but not the only bottleneck. Multiple factors are at play.

## 11. Next Steps After Experiment

**Do NOT implement fixes yet.** This is diagnostic only.

**If experiment strongly implicates fan-out:**
- Investigate Spring `clientOutboundChannel` / broker executor analysis
- Consider custom executor configuration
- Consider removing `/topic/public` from production

**If experiment does NOT implicate fan-out:**
- Investigate DB/Hikari + inbound channel + broker/outbound executor
- Add thread pool monitoring
- Analyze connection pool usage
- Review JVM resource pressure

---

**Experiment Status:** Diagnostic only
**Production Impact:** None (separate k6 test file)
**Backend Changes:** None
**Date:** September 12, 2026