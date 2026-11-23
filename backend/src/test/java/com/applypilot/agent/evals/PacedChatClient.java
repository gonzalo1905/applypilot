package com.applypilot.agent.evals;

import com.applypilot.common.ai.ChatCompletion;
import com.applypilot.common.ai.ChatCompletionClient;
import com.applypilot.common.ai.ChatCompletionRequest;
import com.applypilot.common.ai.ConversationRequest;
import com.applypilot.common.ai.LlmRateLimitedException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Supplier;

/**
 * Wraps the real client for the evals: keeps the tokens sent per minute under the provider's
 * free-tier limit by waiting before a call, retries a call the provider refused with 429, and
 * counts calls and tokens. Without it a full eval run would hit the limit after a few cases
 * and measure the fallbacks instead of the agents.
 */
public final class PacedChatClient implements ChatCompletionClient {

	private static final long MINUTE_MS = 60_000;

	private static final int DEFAULT_ESTIMATE = 2_000;

	private static final int MAX_RATE_LIMIT_RETRIES = 3;

	private final ChatCompletionClient delegate;

	private final int tokensPerMinute;

	/** Calls of the last minute: {time in ms, tokens}. */
	private final Deque<long[]> recent = new ArrayDeque<>();

	private int calls;

	private long promptTokens;

	private long completionTokens;

	private int rateLimitRetries;

	private long waitedMs;

	public PacedChatClient(ChatCompletionClient delegate, int tokensPerMinute) {
		this.delegate = delegate;
		this.tokensPerMinute = tokensPerMinute;
	}

	@Override
	public boolean isConfigured() {
		return delegate.isConfigured();
	}

	@Override
	public ChatCompletion complete(ChatCompletionRequest request) {
		return paced(() -> delegate.complete(request));
	}

	@Override
	public ChatCompletion converse(ConversationRequest request) {
		return paced(() -> delegate.converse(request));
	}

	public synchronized Usage usage() {
		return new Usage(calls, promptTokens, completionTokens, waitedMs);
	}

	public synchronized int rateLimitRetries() {
		return rateLimitRetries;
	}

	public synchronized long waitedMs() {
		return waitedMs;
	}

	/** Totals so far; the difference of two snapshots is what a step cost. */
	public record Usage(int calls, long promptTokens, long completionTokens, long waitedMs) {

		public Usage minus(Usage before) {
			return new Usage(calls - before.calls, promptTokens - before.promptTokens,
					completionTokens - before.completionTokens, waitedMs - before.waitedMs);
		}

	}

	private synchronized ChatCompletion paced(Supplier<ChatCompletion> call) {
		for (int attempt = 0;; attempt++) {
			waitForBudget();
			try {
				ChatCompletion completion = call.get();
				record(completion);
				return completion;
			}
			catch (LlmRateLimitedException ex) {
				if (attempt >= MAX_RATE_LIMIT_RETRIES) {
					throw ex;
				}
				rateLimitRetries++;
				sleep(20_000L * (attempt + 1));
			}
		}
	}

	private void waitForBudget() {
		int estimate = estimate();
		while (true) {
			long now = System.currentTimeMillis();
			while (!recent.isEmpty() && recent.peekFirst()[0] < now - MINUTE_MS) {
				recent.pollFirst();
			}
			long used = recent.stream().mapToLong(entry -> entry[1]).sum();
			if (recent.isEmpty() || used + estimate <= tokensPerMinute) {
				return;
			}
			sleep(recent.peekFirst()[0] + MINUTE_MS - now + 100);
		}
	}

	/** The average size of recent calls, as a guess for the next one. */
	private int estimate() {
		return recent.isEmpty() ? DEFAULT_ESTIMATE
				: (int) recent.stream().mapToLong(entry -> entry[1]).average().orElse(DEFAULT_ESTIMATE);
	}

	private void record(ChatCompletion completion) {
		calls++;
		int prompt = completion.promptTokens() == null ? 0 : completion.promptTokens();
		int generated = completion.completionTokens() == null ? 0 : completion.completionTokens();
		promptTokens += prompt;
		completionTokens += generated;
		recent.addLast(new long[] { System.currentTimeMillis(), prompt + generated > 0 ? prompt + generated
				: DEFAULT_ESTIMATE });
	}

	private void sleep(long millis) {
		if (millis <= 0) {
			return;
		}
		waitedMs += millis;
		try {
			Thread.sleep(millis);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Interrupted while pacing model calls", ex);
		}
	}

}
