package com.chatapp.auth_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.context.annotation.Import;

import com.chatapp.auth_service.config.SecurityConfig;

@SpringBootApplication(exclude = {
    FlywayAutoConfiguration.class
})
@Import(SecurityConfig.class)
public class AuthServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthServiceApplication.class, args);
    }
}