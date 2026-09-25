package com.example.travelagent.advisor;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.moderation.ModerationModel;
import org.springframework.ai.moderation.ModerationPrompt;
import org.springframework.ai.moderation.ModerationResponse;
import org.springframework.ai.moderation.ModerationResult;
import org.springframework.core.Ordered;

/**
 * Screens the user's message with the OpenAI moderation API and answers flagged input
 * with a canned reply, without calling the chat model. Runs before chat memory, so
 * flagged messages are never stored in the conversation.
 */
public class ModerationAdvisor implements CallAdvisor {

	static final String BLOCKED_REPLY = "Sorry, I can't help with that. I'm happy to help you plan a trip, though!";

	static final int ORDER = Ordered.HIGHEST_PRECEDENCE + 100;

	private static final Log logger = LogFactory.getLog(ModerationAdvisor.class);

	private final ModerationModel moderationModel;

	private final boolean failOpen;

	private final Duration timeout;

	private final Executor executor = Executors.newVirtualThreadPerTaskExecutor();

	/**
	 * @param failOpen when the moderation API is unavailable or slower than
	 * {@code timeout}, allow the request (true) or fail it (false)
	 * @param timeout the longest a request waits for the moderation verdict
	 */
	public ModerationAdvisor(ModerationModel moderationModel, boolean failOpen, Duration timeout) {
		this.moderationModel = moderationModel;
		this.failOpen = failOpen;
		this.timeout = timeout;
	}

	@Override
	public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
		UserMessage userMessage = request.prompt().getUserMessage();
		String text = (userMessage != null) ? userMessage.getText() : null;
		if (text != null && !text.isBlank() && isFlagged(text)) {
			logger.warn("Blocked flagged input in conversation " + request.context().get(ChatMemory.CONVERSATION_ID));
			ChatResponse blocked = ChatResponse.builder()
				.generations(List.of(new Generation(new AssistantMessage(BLOCKED_REPLY))))
				.build();
			return ChatClientResponse.builder()
				.chatResponse(blocked)
				.context(request.context())
				.context(AuditLogAdvisor.OUTCOME, "blocked:moderation")
				.build();
		}
		return chain.nextCall(request);
	}

	private boolean isFlagged(String text) {
		try {
			// The OpenAI client's own timeout is shared with chat calls, so enforce a tighter one here
			return CompletableFuture.supplyAsync(() -> this.moderationModel.call(new ModerationPrompt(text)), this.executor)
				.orTimeout(this.timeout.toMillis(), TimeUnit.MILLISECONDS)
				.thenApply(ModerationAdvisor::isFlagged)
				.join();
		}
		catch (CompletionException ex) {
			RuntimeException cause = (ex.getCause() instanceof TimeoutException timeoutEx)
					? new IllegalStateException("Moderation check timed out after " + this.timeout, timeoutEx)
					: (ex.getCause() instanceof RuntimeException runtimeEx) ? runtimeEx : ex;
			if (!this.failOpen) {
				throw cause;
			}
			logger.warn("Moderation check failed; allowing the request (fail-open): " + cause.getMessage());
			return false;
		}
	}

	private static boolean isFlagged(ModerationResponse response) {
		return response.getResult().getOutput().getResults().stream().anyMatch(ModerationResult::isFlagged);
	}

	@Override
	public String getName() {
		return "ModerationAdvisor";
	}

	@Override
	public int getOrder() {
		return ORDER;
	}

}
