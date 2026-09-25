package com.example.travelagent.config;

import java.util.ArrayList;
import java.util.List;

import com.example.travelagent.advisor.AuditLogAdvisor;
import com.example.travelagent.advisor.ModerationAdvisor;
import com.example.travelagent.advisor.PiiRedactionAdvisor;
import com.example.travelagent.advisor.TimeoutAdvisor;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.moderation.ModerationModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Builds the travel-agent {@link ChatClient} and its advisor chain. Each advisor declares
 * its own order; on the way in they run as:
 * <ol>
 * <li>{@link AuditLogAdvisor} - one log line per request (tokens, latency, outcome)</li>
 * <li>{@link PiiRedactionAdvisor} - masks emails, cards and phone numbers</li>
 * <li>{@link ModerationAdvisor} - blocks harmful input before it reaches the model</li>
 * <li>{@link MessageChatMemoryAdvisor} - adds conversation history</li>
 * <li>{@link TimeoutAdvisor} - hard deadline on the model call</li>
 * <li>{@link SimpleLoggerAdvisor} - full prompt/response at DEBUG level</li>
 * </ol>
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AdvisorProperties.class)
public class ChatClientConfig {

	private static final String SYSTEM_PROMPT = """
			You are a friendly, knowledgeable travel agent. Help the user plan trips: suggest destinations,
			itineraries, activities, food, budgets, best times to visit, and practical tips (visas, transport,
			packing). Ask a short clarifying question when key details such as dates, budget or interests are
			missing. Keep answers concise and well structured. If a question is unrelated to travel, briefly
			say so and steer back to travel planning. Do not invent live prices or availability; give typical
			ranges and suggest where to check. Some personal details in messages are masked as [EMAIL],
			[CARD] or [PHONE]; never ask the user to reveal them.
			""";

	@Bean
	ChatClient travelAgentChatClient(ChatClient.Builder builder, ChatMemory chatMemory,
			ObjectProvider<ModerationModel> moderationModel, AdvisorProperties properties) {
		List<Advisor> advisors = new ArrayList<>();
		advisors.add(new AuditLogAdvisor());
		if (properties.piiRedaction().enabled()) {
			advisors.add(new PiiRedactionAdvisor());
		}
		if (properties.moderation().enabled()) {
			advisors.add(new ModerationAdvisor(moderationModel.getObject(), properties.moderation().failOpen(),
					properties.moderation().timeout()));
		}
		advisors.add(MessageChatMemoryAdvisor.builder(chatMemory).build());
		advisors.add(new TimeoutAdvisor(properties.chatTimeout()));
		advisors.add(SimpleLoggerAdvisor.builder().build());
		return builder.defaultSystem(SYSTEM_PROMPT).defaultAdvisors(advisors).build();
	}

}
