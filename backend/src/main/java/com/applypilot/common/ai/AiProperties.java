package com.applypilot.common.ai;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Settings for AI features, bound from {@code app.ai.*}. The connection itself (base URL,
 * API key, timeout) is configured under {@code spring.ai.openai.*}.
 *
 * @param model model id sent to the provider
 * @param temperature randomness of the answer; 0 makes extraction as repeatable as possible
 * @param maxCompletionTokens upper bound on generated tokens per call (cost and latency cap)
 * @param reasoningEffort optional hint for reasoning models ("low", "medium", "high");
 * blank sends nothing, for providers or models that do not know the parameter
 * @param dailyLimit AI calls each user may make per day, shared by every AI feature, to protect
 * the provider quota. A job posting analysis counts once (even when its first answer is
 * rejected and retried); each model call of an agent counts once
 */
@Validated
@ConfigurationProperties(prefix = "app.ai")
public record AiProperties(
		@NotBlank String model,
		@Min(0) @Max(2) double temperature,
		@Min(1) int maxCompletionTokens,
		String reasoningEffort,
		@Min(0) int dailyLimit) {
}
