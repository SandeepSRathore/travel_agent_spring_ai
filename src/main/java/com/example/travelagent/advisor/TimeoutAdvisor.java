package com.example.travelagent.advisor;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;

/**
 * Puts a hard deadline on the model call so a slow or hanging OpenAI response cannot hold
 * a request thread indefinitely. (In Spring AI 2.0.1 the {@code spring.ai.openai.*timeout}
 * properties were observed not to limit chat calls.) Runs inside the chat-memory advisor,
 * so a timed-out exchange is not saved to the conversation.
 */
public class TimeoutAdvisor implements CallAdvisor {

	static final int ORDER = Advisor.DEFAULT_CHAT_MEMORY_PRECEDENCE_ORDER + 100;

	private final Duration timeout;

	private final Executor executor = Executors.newVirtualThreadPerTaskExecutor();

	public TimeoutAdvisor(Duration timeout) {
		this.timeout = timeout;
	}

	@Override
	public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
		try {
			return CompletableFuture.supplyAsync(() -> chain.nextCall(request), this.executor)
				.orTimeout(this.timeout.toMillis(), TimeUnit.MILLISECONDS)
				.join();
		}
		catch (CompletionException ex) {
			if (ex.getCause() instanceof TimeoutException timeoutEx) {
				throw new ChatTimeoutException(this.timeout, timeoutEx);
			}
			throw (ex.getCause() instanceof RuntimeException runtimeEx) ? runtimeEx : ex;
		}
	}

	@Override
	public String getName() {
		return "TimeoutAdvisor";
	}

	@Override
	public int getOrder() {
		return ORDER;
	}

	/**
	 * Thrown when the model does not answer within the configured deadline.
	 */
	public static class ChatTimeoutException extends RuntimeException {

		ChatTimeoutException(Duration timeout, Throwable cause) {
			super("The AI model did not answer within " + timeout, cause);
		}

	}

}
