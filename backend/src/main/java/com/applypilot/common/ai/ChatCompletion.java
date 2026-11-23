package com.applypilot.common.ai;

import java.util.List;

/**
 * The model's answer plus what it cost.
 *
 * @param content the raw text the model produced; callers must validate it. May be empty when
 * the model only asked for tools
 * @param model the model that answered, as reported by the provider
 * @param promptTokens tokens we sent (instructions + input); null if not reported
 * @param completionTokens tokens the model generated; null if not reported
 * @param toolCalls tools the model asked us to run; empty for a plain answer
 */
public record ChatCompletion(String content, String model, Integer promptTokens, Integer completionTokens,
		List<ToolCall> toolCalls) {

	public ChatCompletion {
		toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
	}

	/** A plain answer without tool calls. */
	public ChatCompletion(String content, String model, Integer promptTokens, Integer completionTokens) {
		this(content, model, promptTokens, completionTokens, List.of());
	}

	public boolean hasToolCalls() {
		return !toolCalls.isEmpty();
	}

}
