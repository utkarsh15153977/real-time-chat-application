package com.chatApplication.message_service.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.lang.Nullable;
import org.springframework.messaging.simp.broker.AbstractBrokerMessageHandler;
import org.springframework.messaging.simp.broker.SimpleBrokerMessageHandler;
import org.springframework.messaging.simp.broker.SubscriptionRegistry;
import org.springframework.messaging.support.AbstractSubscribableChannel;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Registers the {@link BrokerChannelDiagnosticInterceptor} on the
 * brokerChannel after all singletons are initialized.
 *
 * This interceptor provides evidence of subscription availability
 * at the exact moment SimpMessagingTemplate.convertAndSend() publishes
 * a message to the broker.
 *
 * PURPOSE: Investigation only. Remove after RCA is complete.
 */
@Component
public class BrokerChannelInstrumentation implements InitializingBean {

    private static final Logger log =
            LoggerFactory.getLogger("STOMP_DIAGNOSTIC");

    private final AbstractSubscribableChannel brokerChannel;
    @Nullable
    private final SubscriptionRegistry subscriptionRegistry;

    public BrokerChannelInstrumentation(
            AbstractSubscribableChannel brokerChannel,
            List<AbstractBrokerMessageHandler> brokerHandlers) {

        this.brokerChannel = brokerChannel;

        this.subscriptionRegistry = brokerHandlers.stream()
                .filter(h -> h instanceof SimpleBrokerMessageHandler)
                .map(h -> ((SimpleBrokerMessageHandler) h)
                        .getSubscriptionRegistry())
                .findFirst()
                .orElse(null);

        if (this.subscriptionRegistry == null) {
            log.warn("[BROKER] No SimpleBrokerMessageHandler found. " +
                    "BrokerChannel diagnostic interceptor NOT registered.");
        }
    }

    @Override
    public void afterPropertiesSet() {
        if (this.subscriptionRegistry == null) {
            return;
        }

        this.brokerChannel.addInterceptor(
                new BrokerChannelDiagnosticInterceptor(
                        this.subscriptionRegistry));

        log.info("[BROKER] BrokerChannel diagnostic interceptor " +
                "registered. Registry={}",
                this.subscriptionRegistry.getClass().getSimpleName());
    }
}
