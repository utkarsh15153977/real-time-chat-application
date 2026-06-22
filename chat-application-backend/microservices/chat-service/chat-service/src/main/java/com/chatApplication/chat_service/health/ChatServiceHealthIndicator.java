package com.chatApplication.chat_service.health;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
public class ChatServiceHealthIndicator implements HealthIndicator {
    @Override
    public Health health() {

        boolean serviceRunning = true;

        if (serviceRunning) {
            return Health.up()
                    .withDetail("service", "Chat Service")
                    .withDetail("status", "Running")
                    .withDetail("websocket", "Available")
                    .build();
        }

        return Health.down()
                .withDetail("service", "Chat Service")
                .withDetail("status", "Stopped")
                .build();
    }
}
