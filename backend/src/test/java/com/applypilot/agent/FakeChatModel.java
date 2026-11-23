package com.applypilot.agent;

import com.applypilot.common.ai.ChatCompletion;
import com.applypilot.common.ai.ChatCompletionClient;
import com.applypilot.common.ai.ChatCompletionRequest;
import com.applypilot.common.ai.ConversationRequest;
import com.applypilot.common.ai.ToolCall;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.Supplier;

/**
 * A fake model for agent tests: answers from a script, one entry per call in order, and
 * remembers every request. Costs nothing and never reaches the network.
 */
final class FakeChatModel implements ChatCompletionClient {

	static final String MODEL = "fake-model";

	private final Deque<Supplier<ChatCompletion>> script = new ArrayDeque<>();

	final List<ChatCompletionRequest> structuredRequests = new ArrayList<>();

	final List<ConversationRequest> conversations = new ArrayList<>();

	boolean configured = true;

	FakeChatModel answers(String... contents) {
		for (String content : contents) {
			script.add(() -> new ChatCompletion(content, MODEL, 100, 20));
		}
		return this;
	}

	FakeChatModel callsTools(ToolCall... calls) {
		script.add(() -> new ChatCompletion("", MODEL, 100, 10, List.of(calls)));
		return this;
	}

	FakeChatModel fails(Supplier<RuntimeException> failure) {
		script.add(() -> {
			throw failure.get();
		});
		return this;
	}

	int remaining() {
		return script.size();
	}

	@Override
	public boolean isConfigured() {
		return configured;
	}

	@Override
	public ChatCompletion complete(ChatCompletionRequest request) {
		structuredRequests.add(request);
		return next();
	}

	@Override
	public ChatCompletion converse(ConversationRequest request) {
		conversations.add(request);
		return next();
	}

	private ChatCompletion next() {
		if (script.isEmpty()) {
			throw new AssertionError("The model was called more times than the test expected");
		}
		return script.poll().get();
	}

	static ToolCall search(String id, String term) {
		return new ToolCall(id, CvTools.SEARCH, "{\"term\": \"" + term + "\"}");
	}

}
