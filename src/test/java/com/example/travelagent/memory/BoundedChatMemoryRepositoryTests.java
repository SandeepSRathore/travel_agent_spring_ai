package com.example.travelagent.memory;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

import static org.assertj.core.api.Assertions.assertThat;

class BoundedChatMemoryRepositoryTests {

	private final AtomicLong nanos = new AtomicLong();

	private final List<Message> messages = List.of(new UserMessage("Plan a trip to Goa"));

	@Test
	void storesAndReturnsMessages() {
		var repository = repository(10, Duration.ofHours(1));
		repository.saveAll("c1", this.messages);

		assertThat(repository.findByConversationId("c1")).containsExactlyElementsOf(this.messages);
		assertThat(repository.findByConversationId("unknown")).isEmpty();
	}

	@Test
	void evictsConversationsBeyondTheLimit() {
		var repository = repository(3, Duration.ofHours(1));
		for (int i = 0; i < 50; i++) {
			repository.saveAll("c" + i, this.messages);
		}

		assertThat(repository.size()).isLessThanOrEqualTo(3);
	}

	@Test
	void forgetsIdleConversations() {
		var repository = repository(10, Duration.ofMinutes(30));
		repository.saveAll("c1", this.messages);

		this.nanos.addAndGet(Duration.ofMinutes(31).toNanos());

		assertThat(repository.findByConversationId("c1")).isEmpty();
		assertThat(repository.size()).isZero();
	}

	@Test
	void deletesConversation() {
		var repository = repository(10, Duration.ofHours(1));
		repository.saveAll("c1", this.messages);
		repository.deleteByConversationId("c1");

		assertThat(repository.findConversationIds()).isEmpty();
	}

	private BoundedChatMemoryRepository repository(long maxConversations, Duration idleTimeout) {
		return new BoundedChatMemoryRepository(maxConversations, idleTimeout, this.nanos::get, Runnable::run);
	}

}
