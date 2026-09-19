package com.chatApplication.message_service.config;

import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Generates a unique, stable identifier for this application instance.
 * <p>
 * The ID is a random UUID generated once at application startup and
 * reused for the lifetime of the JVM process. It is used by
 * {@code RedisMessagePublisher} and {@code RedisMessageSubscriber}
 * to prevent self-relay of Redis Pub/Sub messages — i.e., an instance
 * that publishes a message to the "chat:messages" channel will ignore
 * its own publication when it arrives back via the Redis subscriber.
 * <p>
 * Thread-safety: the instance ID is assigned once in the constructor
 * and is effectively immutable thereafter.
 */
@Component
public class InstanceIdentity {

    private final String instanceId;

    public InstanceIdentity() {
        this.instanceId = UUID.randomUUID().toString();
    }

    /**
     * Returns the unique identifier for this application instance.
     *
     * @return a UUID string, never null
     */
    public String getInstanceId() {
        return instanceId;
    }
}
