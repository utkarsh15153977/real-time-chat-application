package com.chatbackend.chat_application_backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class ChatApplicationBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(ChatApplicationBackendApplication.class, args);
	}

}
