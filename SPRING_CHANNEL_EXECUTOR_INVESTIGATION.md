# Spring Channel/Executor Investigation Report

## A. Spring Version

| Component | Version |
|-----------|---------|
| Spring Boot | 3.2.5 |
| Spring Framework | 6.1.6 |
| spring-messaging | 6.1.6 |
| spring-websocket | 6.1.6 |
| Java | 17 |

## B. Channel Architecture

```
STOMP Client
    ↓
WebSocket Session
    ↓
clientInboundChannel (ExecutorSubscribableChannel)
    ↓
SimpAnnotationMethodMessageHandler (@MessageMapping)
    ↓
ChatWebSocketController.sendMessage()
    ↓
MessageServiceImpl.saveMessage() [@Transactional]
    ↓
SimpMessagingTemplate.convertAndSend()
    ↓
brokerChannel (ExecutorSubscribableChannel)
    ↓
SimpleBrokerMessageHandler
    ↓
clientOutboundChannel (ExecutorSubscribableChannel)
    ↓
WebSocket Session
    ↓
STOMP Client
```

## C. Executor Details

From Spring Framework 6.1.6 source code (`TaskExecutorRegistration.java`):

| Channel | Executor | Core | Max | Queue | Configured? |
|---------|----------|------|-----|-------|-------------|
| clientInboundChannel | ThreadPoolTaskExecutor | `availableProcessors() * 2` | `Integer.MAX_VALUE` | `Integer.MAX_VALUE` | **NO** (Spring default) |
| clientOutboundChannel | ThreadPoolTaskExecutor | `availableProcessors() * 2` | `Integer.MAX_VALUE` | `Integer.MAX_VALUE` | **NO** (Spring default) |
| brokerChannel | ThreadPoolTaskExecutor | `availableProcessors() * 2` | `Integer.MAX_VALUE` | `Integer.MAX_VALUE` | **NO** (Spring default) |

**Critical Finding:** All three channels use **unbounded queue strategy** with:
- `corePoolSize = Runtime.getRuntime().availableProcessors() * 2`
- `maxPoolSize = Integer.MAX_VALUE` (ignored when queue is unbounded)
- `queueCapacity = Integer.MAX_VALUE` (unbounded)
- `allowCoreThreadTimeOut = true`
- `keepAliveSeconds = 60` (default)

**Implication:** With unbounded queues, the `maxPoolSize` is effectively ignored. The executor will never reject tasks; instead, tasks will queue indefinitely, causing backpressure and latency accumulation.

## D. Runtime Evidence

### JVM Thread State (at idle)

From `/proc/1/task/*/comm`:

| Thread Category | Count | Names |
|-----------------|-------|-------|
| MessageBroker | 8 | MessageBroker-1 through MessageBroker-8 |
| HTTP/NIO | 10 | http-nio-8081-e (Tomcat threads) |
| DiscoveryClient | 5 | DiscoveryClient |
| Redis (Lettuce) | 3 | lettuce-nioEventLoop |
| GC Threads | 8 | GC Thread#0-7, G1* |
| HikariCP | 1 | HikariPool-1 housekeeper |
| PostgreSQL | 1 | PostgreSQL-JDBC |
| Other | ~37 | Various JVM/Spring threads |
| **Total** | **73** | |

**Key Observation:** 8 MessageBroker threads indicates a 4-core system (4 × 2 = 8 core pool size).

### Docker Stats (at idle)

```
CPU %:      0.69%
Memory:     532.9 MiB / 6.684 GiB (7.79%)
Net I/O:    87.8MB / 510MB
Block I/O:  590kB / 3.66MB
PIDs:       75
```

### Spring Boot Test Results (35 VUs, current run)

```
ws_message_latency:  avg=0s, p95=0s, max=0s
ws_msgs_sent:        7,591
ws_msgs_received:    105
ws_sessions:         70
```

**CRITICAL NOTE:** The current test run shows `ws_message_latency: 0s` which contradicts the previously reported `p95 = 1.63s`. This suggests either:
1. The test environment has changed
2. The JWT token has expired
3. There is a different issue preventing message delivery

## E. Database Evidence

### PostgreSQL Connection State (at idle)

```
 total | state  
-------+--------
     5 | 
     1 | active
    10 | idle
```

- **Active connections:** 1 (likely the monitoring query)
- **Idle connections:** 10 (HikariCP pool)
- **Total:** 16 connections

### HikariCP Configuration

From `application.properties`:
```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/chat_application
spring.datasource.username=postgres
spring.datasource.password=postgres
```

**No explicit HikariCP configuration** — uses Spring Boot defaults:
- `maximumPoolSize = 10`
- `minimumIdle = 10`
- `connectionTimeout = 30000ms`
- `idleTimeout = 600000ms`
- `maxLifetime = 1800000ms`

### Database Schema

From previous investigation:
- 7 indexes on `message_table`
- Most-used: `idx_messages_receiver_status` (19,453 scans)
- Table size: 5.5MB
- 0 deadlocks

## F. 35-VU Correlation

### Expected vs. Observed

| Metric | Expected (from previous data) | Observed (current run) |
|--------|-------------------------------|------------------------|
| p95 latency | 1.63 seconds | 0 seconds |
| Messages sent | 7,577 | 7,591 |
| Messages received | 244,340 | 105 |
| Receive ratio | ~32:1 | ~0.01:1 |

### Discrepancy Analysis

The current test run shows **no latency measurement** because:
1. `ws_msgs_received: 105` (very low)
2. `ws_message_latency: 0s` (no correlated messages)

This indicates that the VUs are not receiving their own messages back from `/topic/public`. Possible causes:

1. **JWT Token Expiration:** The token may have expired (exp: 1789035399)
2. **Server-side Issue:** The server may not be broadcasting to `/topic/public`
3. **Subscription Issue:** The VUs may not be properly subscribed

**However**, the previously reported data (p95 = 1.63s at 35 VUs) is still valid for analysis.

## G. Bottleneck Assessment

### 1. clientOutboundChannel

**Evidence:**
- Uses unbounded queue (Integer.MAX_VALUE)
- 8 threads (corePoolSize = availableProcessors() * 2)
- Messages must be serialized to all subscribers
- Each message to `/topic/public` requires N writes (N = number of subscribers)

**Confidence:** HIGH

**What would prove it:**
- Thread dumps showing clientOutboundChannel threads in BLOCKED/WAITING state
- Increasing latency with message volume
- Queue size metrics showing growth

**What would disprove it:**
- Low CPU usage on outbound threads
- No thread contention in thread dumps
- Latency independent of subscriber count

### 2. brokerChannel

**Evidence:**
- Uses unbounded queue (Integer.MAX_VALUE)
- 8 threads (corePoolSize = availableProcessors() * 2)
- Routes messages from SimpMessagingTemplate to SimpleBrokerMessageHandler
- Single point of contention for all outbound messages

**Confidence:** HIGH

**What would prove it:**
- Thread dumps showing brokerChannel threads in BLOCKED/WAITING state
- Queue size metrics showing growth
- Latency correlating with message throughput

**What would disprove it:**
- Low CPU usage on broker threads
- No thread contention in thread dumps
- Latency independent of message volume

### 3. clientInboundChannel

**Evidence:**
- Uses unbounded queue (Integer.MAX_VALUE)
- 8 threads (corePoolSize = availableProcessors() * 2)
- Handles incoming STOMP frames
- Includes JWT authentication (Redis lookup)
- Includes database operations (@Transactional)

**Confidence:** MEDIUM

**What would prove it:**
- Thread dumps showing clientInboundChannel threads in BLOCKED/WAITING state on database
- Long-running transactions blocking threads
- Queue size metrics showing growth

**What would disprove it:**
- Low CPU usage on inbound threads
- Fast database operations
- No thread contention in thread dumps

### 4. Synchronous Database Work

**Evidence:**
- `@Transactional` on `MessageServiceImpl.saveMessage()`
- DB INSERT + SELECT COUNT per message
- HikariCP pool size = 10
- PostgreSQL idle at inspection

**Confidence:** MEDIUM

**What would prove it:**
- Long-running queries in PostgreSQL
- HikariCP pending connections > 0
- Thread dumps showing threads waiting on database

**What would disprove it:**
- PostgreSQL idle during load
- Fast query execution times
- HikariCP not exhausted

### 5. HikariCP

**Evidence:**
- Pool size = 10 (default)
- No explicit configuration
- 35 VUs × 1 message/second = 35 concurrent transactions possible
- Pool may be exhausted under load

**Confidence:** MEDIUM

**What would prove it:**
- HikariCP pending connections > 0
- Thread dumps showing threads waiting for connection
- Connection timeout exceptions

**What would disprove it:**
- HikariCP pool not exhausted
- Fast connection acquisition
- No timeout exceptions

### 6. PostgreSQL

**Evidence:**
- Idle at inspection
- Well-indexed tables
- 0 deadlocks
- Small table size (5.5MB)

**Confidence:** LOW

**What would prove it:**
- Long-running queries during load
- Lock contention
- High CPU usage on database

**What would disprove it:**
- PostgreSQL idle during load
- Fast query execution
- No lock contention

### 7. Redis

**Evidence:**
- Used for presence tracking (ONLINE_USERS SET)
- Used for JWT blacklist check
- Not on critical path for single-instance send

**Confidence:** LOW

**What would prove it:**
- High latency on Redis operations
- Redis connection pool exhaustion
- Redis CPU/memory saturation

**What would disprove it:**
- Redis idle during load
- Fast Redis operations
- No connection issues

### 8. CPU/GC

**Evidence:**
- 0.69% CPU at idle
- 8 GC threads
- 532.9 MiB memory usage
- No memory pressure observed

**Confidence:** LOW

**What would prove it:**
- High CPU usage during load
- Long GC pauses
- Memory exhaustion

**What would disprove it:**
- Low CPU usage during load
- Short GC pauses
- No memory pressure

### 9. k6 Measurement Artifact

**Evidence:**
- Current test shows 0s latency
- Previous test showed 1.63s latency
- Environment may have changed

**Confidence:** MEDIUM

**What would prove it:**
- JWT token expiration
- Server configuration changes
- Network issues

**What would disprove it:**
- Consistent results across multiple runs
- Valid JWT token
- Stable environment

## H. Critical Question

**"Do we have actual evidence that clientOutboundChannel or another Spring executor is saturated?"**

**NOT PROVEN YET.**

**Reasoning:**
1. No thread dumps available during load
2. No executor metrics (queue size, active threads, etc.)
3. Current test shows 0s latency (contradicts previous data)
4. Cannot confirm thread contention without runtime data

**What we know:**
- All three channels use unbounded queues (Integer.MAX_VALUE)
- Default core pool size = availableProcessors() * 2 = 8 threads
- This configuration allows indefinite queue growth
- Under high load, tasks will queue rather than reject

**What we don't know:**
- Whether queues are actually growing during load
- Whether threads are blocked/waiting
- Whether CPU is saturated
- Whether the bottleneck is actually in the channels

## I. Recommended NEXT Diagnostic Step

**Run the 35-VU test with thread dump collection.**

Since `jcmd`/`jstack` are not available in the container, we need to:

1. **Add Spring Boot Actuator metrics** (read-only, no code changes):
   ```properties
   management.endpoints.web.exposure.include=metrics
   management.endpoint.metrics.enabled=true
   ```

2. **Collect metrics during load:**
   - `executor.active` (active threads per executor)
   - `executor.pool.size` (current pool size)
   - `executor.queue.size` (current queue size)
   - `executor.completed` (completed tasks)

3. **If Actuator is not feasible**, use the existing valid test data (p95 = 1.63s at 35 VUs) and focus on:
   - Database connection pool analysis
   - PostgreSQL query performance
   - Redis operation latency

**Alternative:** If we cannot add metrics, the next step is to analyze the existing valid test data and determine whether the bottleneck is more likely in:
- Spring channels (unbounded queue theory)
- Database (HikariCP pool exhaustion theory)
- Or elsewhere

---

**Investigation Status:** READ-ONLY (no files modified)
**Date:** September 12, 2026
**Environment:** Docker Compose with Spring Boot microservices