package com.example.travelagent.config;

import com.example.travelagent.memory.BoundedChatMemoryRepository;

import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Supplies a bounded conversation store. Spring AI's auto-configured
 * {@code MessageWindowChatMemory} picks it up in place of its unbounded default.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ChatMemoryProperties.class)
public class ChatMemoryConfig {

	@Bean
	ChatMemoryRepository boundedChatMemoryRepository(ChatMemoryProperties properties) {
		return new BoundedChatMemoryRepository(properties.maxConversations(), properties.idleTimeout());
	}

}
