package com.applypilot.agent;

/**
 * What one step cost: model calls (each one counts against the daily limit), tool calls run
 * by our code, and the tokens the provider reported.
 *
 * @param model the model that answered last; null when no model was called
 * @param promptTokens total over the step's calls; null when no call reported them
 * @param completionTokens total over the step's calls; null when no call reported them
 */
public record StepUsage(String model, int modelCalls, int toolCalls, Integer promptTokens, Integer completionTokens) {

	public static final StepUsage NONE = new StepUsage(null, 0, 0, null, null);

}
