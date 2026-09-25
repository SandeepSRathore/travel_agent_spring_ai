package com.example.travelagent.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Limits for the in-memory conversation store, bound from {@code travel-agent.chat-memory.*}.
 *
 * @param maxConversations conversations kept at most; the least recently used are evicted
 * @param idleTimeout a conversation is forgotten after this long without a message
 */
@ConfigurationProperties("travel-agent.chat-memory")
public record ChatMemoryProperties(@DefaultValue("10000") long maxConversations,
		@DefaultValue("2h") Duration idleTimeout) {

}
