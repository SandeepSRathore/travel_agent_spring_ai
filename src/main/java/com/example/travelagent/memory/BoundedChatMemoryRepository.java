package com.example.travelagent.memory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;

import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.Message;

/**
 * In-memory {@link ChatMemoryRepository} that cannot grow without limit: it keeps at most
 * {@code maxConversations} conversations and forgets any conversation that has not been
 * used for {@code idleTimeout}. Replaces Spring AI's default repository, which keeps
 * every conversation forever.
 */
public class BoundedChatMemoryRepository implements ChatMemoryRepository {

	private final Cache<String, List<Message>> conversations;

	public BoundedChatMemoryRepository(long maxConversations, Duration idleTimeout) {
		this(maxConversations, idleTimeout, Ticker.systemTicker(), null);
	}

	BoundedChatMemoryRepository(long maxConversations, Duration idleTimeout, Ticker ticker, Executor executor) {
		Caffeine<Object, Object> builder = Caffeine.newBuilder()
			.maximumSize(maxConversations)
			.expireAfterAccess(idleTimeout)
			.ticker(ticker);
		if (executor != null) {
			builder.executor(executor);
		}
		this.conversations = builder.build();
	}

	@Override
	public List<String> findConversationIds() {
		return new ArrayList<>(this.conversations.asMap().keySet());
	}

	@Override
	public List<Message> findByConversationId(String conversationId) {
		List<Message> messages = this.conversations.getIfPresent(conversationId);
		return (messages != null) ? new ArrayList<>(messages) : new ArrayList<>();
	}

	@Override
	public void saveAll(String conversationId, List<Message> messages) {
		this.conversations.put(conversationId, List.copyOf(messages));
	}

	@Override
	public void deleteByConversationId(String conversationId) {
		this.conversations.invalidate(conversationId);
	}

	long size() {
		this.conversations.cleanUp();
		return this.conversations.estimatedSize();
	}

}
