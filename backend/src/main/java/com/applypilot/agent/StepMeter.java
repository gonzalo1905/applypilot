package com.applypilot.agent;

import com.applypilot.common.ai.ChatCompletion;
import java.util.UUID;

/**
 * Collects what one step costs while it runs: model calls, tool calls and tokens. One meter
 * per step; not shared between threads.
 */
public final class StepMeter {

	private final UUID userId;

	private int modelCalls;

	private int toolCalls;

	private Integer promptTokens;

	private Integer completionTokens;

	private String model;

	public StepMeter(UUID userId) {
		this.userId = userId;
	}

	/** The user whose daily limit pays for this step's model calls. */
	public UUID userId() {
		return userId;
	}

	void recordModelCall(ChatCompletion completion) {
		modelCalls++;
		model = completion.model();
		promptTokens = add(promptTokens, completion.promptTokens());
		completionTokens = add(completionTokens, completion.completionTokens());
	}

	void recordToolCall() {
		toolCalls++;
	}

	public int toolCalls() {
		return toolCalls;
	}

	public StepUsage usage() {
		return new StepUsage(model, modelCalls, toolCalls, promptTokens, completionTokens);
	}

	private static Integer add(Integer total, Integer value) {
		if (value == null) {
			return total;
		}
		return total == null ? value : total + value;
	}

}
