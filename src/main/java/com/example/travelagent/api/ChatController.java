package com.example.travelagent.api;

import java.util.UUID;

import com.example.travelagent.advisor.TimeoutAdvisor;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.ai.chat.client.ChatClient;
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

	private final ChatClient chatClient;

	public ChatController(ChatClient chatClient) {
		this.chatClient = chatClient;
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
		catch (TimeoutAdvisor.ChatTimeoutException ex) {
			logger.warn(ex.getMessage());
			throw new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT,
					"The AI took too long to answer. Please try again.", ex);
		}
		catch (RuntimeException ex) {
			logger.error("Chat request to the AI model failed", ex);
			throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
					"The AI service could not answer right now. Please try again.", ex);
		}
	}

	public record ChatRequest(@NotBlank @Size(max = 4000) String message, @Pattern(regexp = "[A-Za-z0-9-]{1,64}") String conversationId) {
	}

	public record ChatResponse(String conversationId, String reply) {
	}

}
