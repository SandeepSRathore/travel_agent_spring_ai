package com.example.travelagent.advisor;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.moderation.Generation;
import org.springframework.ai.moderation.Moderation;
import org.springframework.ai.moderation.ModerationModel;
import org.springframework.ai.moderation.ModerationResponse;
import org.springframework.ai.moderation.ModerationResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class ModerationAdvisorTests {

	private static final Duration TIMEOUT = Duration.ofSeconds(2);

	private final ChatClientRequest request = new ChatClientRequest(new Prompt("some user input"), Map.of());

	private final CallAdvisorChain chain = mock(CallAdvisorChain.class);

	@Test
	void flaggedInputIsAnsweredWithoutCallingTheModel() {
		var advisor = new ModerationAdvisor(moderation(true), true, TIMEOUT);

		ChatClientResponse response = advisor.adviseCall(this.request, this.chain);

		verify(this.chain, never()).nextCall(any());
		assertThat(response.chatResponse().getResult().getOutput().getText())
			.isEqualTo(ModerationAdvisor.BLOCKED_REPLY);
		assertThat(response.context()).containsEntry(AuditLogAdvisor.OUTCOME, "blocked:moderation");
	}

	@Test
	void cleanInputContinuesDownTheChain() {
		var advisor = new ModerationAdvisor(moderation(false), true, TIMEOUT);
		ChatClientResponse downstream = ChatClientResponse.builder().build();
		given(this.chain.nextCall(this.request)).willReturn(downstream);

		assertThat(advisor.adviseCall(this.request, this.chain)).isSameAs(downstream);
	}

	@Test
	void moderationOutageFailsOpenWhenConfigured() {
		var advisor = new ModerationAdvisor(failingModeration(), true, TIMEOUT);
		ChatClientResponse downstream = ChatClientResponse.builder().build();
		given(this.chain.nextCall(this.request)).willReturn(downstream);

		assertThat(advisor.adviseCall(this.request, this.chain)).isSameAs(downstream);
	}

	@Test
	void moderationOutageFailsClosedWhenConfigured() {
		var advisor = new ModerationAdvisor(failingModeration(), false, TIMEOUT);

		assertThatIllegalStateException().isThrownBy(() -> advisor.adviseCall(this.request, this.chain));
		verify(this.chain, never()).nextCall(any());
	}

	@Test
	void slowModerationTimesOutAndFailsOpen() {
		var advisor = new ModerationAdvisor(slowModeration(), true, Duration.ofMillis(100));
		ChatClientResponse downstream = ChatClientResponse.builder().build();
		given(this.chain.nextCall(this.request)).willReturn(downstream);

		long start = System.nanoTime();
		assertThat(advisor.adviseCall(this.request, this.chain)).isSameAs(downstream);
		assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(2));
	}

	@Test
	void slowModerationTimesOutAndFailsClosed() {
		var advisor = new ModerationAdvisor(slowModeration(), false, Duration.ofMillis(100));

		assertThatIllegalStateException().isThrownBy(() -> advisor.adviseCall(this.request, this.chain))
			.withMessageContaining("timed out");
		verify(this.chain, never()).nextCall(any());
	}

	private static ModerationModel slowModeration() {
		return (prompt) -> {
			try {
				Thread.sleep(Duration.ofSeconds(10));
			}
			catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
			}
			throw new IllegalStateException("should have timed out first");
		};
	}

	private static ModerationModel moderation(boolean flagged) {
		Moderation moderation = Moderation.builder()
			.id("modr-test")
			.model("omni-moderation-latest")
			.results(List.of(ModerationResult.builder().flagged(flagged).build()))
			.build();
		return (prompt) -> new ModerationResponse(new Generation(moderation));
	}

	private static ModerationModel failingModeration() {
		return (prompt) -> {
			throw new IllegalStateException("moderation API unavailable");
		};
	}

}
