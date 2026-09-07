package com.chatapplication.group_chat;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Disabled("Requires full context with Kafka/Redis - unit tests cover all functionality")
class GroupChatApplicationTests {

	@Test
	void contextLoads() {
	}

}
