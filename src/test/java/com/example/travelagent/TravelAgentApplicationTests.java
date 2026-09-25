package com.example.travelagent;

import com.example.travelagent.memory.BoundedChatMemoryRepository;
import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "spring.ai.openai.api-key=test-key")
class TravelAgentApplicationTests {

	@Autowired
	private ChatMemoryRepository chatMemoryRepository;

	@Test
	void usesBoundedChatMemory() {
		assertThat(this.chatMemoryRepository).isInstanceOf(BoundedChatMemoryRepository.class);
	}

}
