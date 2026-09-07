package com.chatApplication.api_gateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.test.context.ActiveProfiles;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@AutoConfigureWebTestClient
@ActiveProfiles("test")
class GatewayRoutesConfigTest {

    @Autowired
    private RouteLocator routeLocator;

    @Autowired
    private org.springframework.test.web.reactive.server.WebTestClient webTestClient;

    @Test
    void routes_shouldBeConfigured() {
        assertNotNull(routeLocator);
        StepVerifier.create(routeLocator.getRoutes().collectList())
            .assertNext(routes -> {
                assert routes.size() >= 6 : "Expected at least 6 routes";
                var routeIds = routes.stream()
                    .map(org.springframework.cloud.gateway.route.Route::getId)
                    .toList();
                assert routeIds.contains("auth-service") : "Missing auth-service route";
                assert routeIds.contains("user-service") : "Missing user-service route";
                assert routeIds.contains("chat-service") : "Missing chat-service route";
                assert routeIds.contains("message-service") : "Missing message-service route";
                assert routeIds.contains("notification-service") : "Missing notification-service route";
                assert routeIds.contains("group-chat") : "Missing group-chat route";
            })
            .verifyComplete();
    }

    @Test
    void actuatorEndpoint_shouldBePermitted() {
        webTestClient.get()
            .uri("/actuator/health")
            .exchange()
            .expectStatus().isOk();
    }
}
