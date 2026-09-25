package com.example.travelagent.advisor;

import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.prompt.Prompt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class TimeoutAdvisorTests {

	private final ChatClientRequest request = new ChatClientRequest(new Prompt("hi"), Map.of());

	private final CallAdvisorChain chain = mock(CallAdvisorChain.class);

	@Test
	void returnsResponseThatArrivesInTime() {
		ChatClientResponse downstream = ChatClientResponse.builder().build();
		given(this.chain.nextCall(this.request)).willReturn(downstream);

		assertThat(new TimeoutAdvisor(Duration.ofSeconds(2)).adviseCall(this.request, this.chain)).isSameAs(downstream);
	}

	@Test
	void failsFastWhenTheModelIsTooSlow() {
		given(this.chain.nextCall(this.request)).willAnswer((invocation) -> {
			Thread.sleep(Duration.ofSeconds(10));
			return ChatClientResponse.builder().build();
		});

		long start = System.nanoTime();
		assertThatExceptionOfType(TimeoutAdvisor.ChatTimeoutException.class)
			.isThrownBy(() -> new TimeoutAdvisor(Duration.ofMillis(100)).adviseCall(this.request, this.chain));
		assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(2));
	}

	@Test
	void propagatesModelErrorsUnwrapped() {
		given(this.chain.nextCall(this.request)).willThrow(new IllegalStateException("rate limited"));

		assertThatExceptionOfType(IllegalStateException.class)
			.isThrownBy(() -> new TimeoutAdvisor(Duration.ofSeconds(2)).adviseCall(this.request, this.chain))
			.withMessage("rate limited");
	}

}
