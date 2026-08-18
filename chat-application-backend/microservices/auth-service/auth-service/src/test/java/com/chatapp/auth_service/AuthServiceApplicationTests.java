package com.chatapp.auth_service;

import com.chatapp.auth_service.config.TestConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestConfig.class)
class AuthServiceApplicationTests {

    @Test
    void contextLoads() {
        // Test passes when ApplicationContext loads successfully
    }
}