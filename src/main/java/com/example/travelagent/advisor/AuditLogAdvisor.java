package com.example.travelagent.advisor;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.core.Ordered;

/**
 * Outermost advisor: writes one INFO line per chat request with the conversation id,
 * outcome, model, token usage and latency. Message content is never logged, so the line
 * is safe to ship to production log storage.
 */
public class AuditLogAdvisor implements CallAdvisor {

	/** Response-context key other advisors set when they short-circuit a request. */
	public static final String OUTCOME = "travel_agent.outcome";

	private static final Log logger = LogFactory.getLog(AuditLogAdvisor.class);

	@Override
	public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
		long start = System.nanoTime();
		Object conversationId = request.context().get(ChatMemory.CONVERSATION_ID);
		try {
			ChatClientResponse response = chain.nextCall(request);
			log(conversationId, response, elapsedMillis(start));
			return response;
		}
		catch (RuntimeException ex) {
			logger.info("chat conversation=%s outcome=error error=%s durationMs=%d".formatted(conversationId,
					ex.getClass().getSimpleName(), elapsedMillis(start)));
			throw ex;
		}
	}

	private void log(Object conversationId, ChatClientResponse response, long durationMs) {
		Object outcome = response.context().getOrDefault(OUTCOME, "ok");
		Object redactions = response.context().getOrDefault(PiiRedactionAdvisor.REDACTION_COUNT, 0);
		String model = null;
		Usage usage = null;
		if (response.chatResponse() != null) {
			ChatResponseMetadata metadata = response.chatResponse().getMetadata();
			model = metadata.getModel();
			usage = metadata.getUsage();
		}
		logger.info("chat conversation=%s outcome=%s model=%s promptTokens=%s completionTokens=%s totalTokens=%s piiRedactions=%s durationMs=%d"
			.formatted(conversationId, outcome, model, (usage != null) ? usage.getPromptTokens() : null,
					(usage != null) ? usage.getCompletionTokens() : null,
					(usage != null) ? usage.getTotalTokens() : null, redactions, durationMs));
	}

	private static long elapsedMillis(long startNanos) {
		return (System.nanoTime() - startNanos) / 1_000_000;
	}

	@Override
	public String getName() {
		return "AuditLogAdvisor";
	}

	@Override
	public int getOrder() {
		return Ordered.HIGHEST_PRECEDENCE;
	}

}
