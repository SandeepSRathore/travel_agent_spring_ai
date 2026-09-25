package com.example.travelagent.api;

import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api")
public class ChatController {

	private static final Log logger = LogFactory.getLog(ChatController.class);

	private static final String SYSTEM_PROMPT = """
			You are a friendly, knowledgeable travel agent. Help the user plan trips: suggest destinations,
			itineraries, activities, food, budgets, best times to visit, and practical tips (visas, transport,
			packing). Ask a short clarifying question when key details such as dates, budget or interests are
			missing. Keep answers concise and well structured. If a question is unrelated to travel, briefly
			say so and steer back to travel planning. Do not invent live prices or availability; give typical
			ranges and suggest where to check.
			""";

	private final ChatClient chatClient;

	public ChatController(ChatClient.Builder chatClientBuilder, ChatMemory chatMemory) {
		this.chatClient = chatClientBuilder.defaultSystem(SYSTEM_PROMPT)
			.defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
			.build();
	}

	@PostMapping("/chat")
	public ChatResponse chat(@Valid @RequestBody ChatRequest request) {
		String conversationId = (request.conversationId() != null) ? request.conversationId()
				: UUID.randomUUID().toString();
		try {
			String reply = this.chatClient.prompt()
				.user(request.message())
				.advisors((advisor) -> advisor.param(ChatMemory.CONVERSATION_ID, conversationId))
				.call()
				.content();
			return new ChatResponse(conversationId, reply);
		}
		catch (RuntimeException ex) {
			logger.error("Chat request to the AI model failed", ex);
			throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
					"The AI service could not answer right now. Please try again.", ex);
		}
	}

	public record ChatRequest(@NotBlank @Size(max = 4000) String message, @Size(max = 64) String conversationId) {
	}

	public record ChatResponse(String conversationId, String reply) {
	}

}
