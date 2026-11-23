package com.applypilot.agent;

import com.applypilot.analysis.AiDailyLimitReachedException;
import com.applypilot.analysis.AiUsageLimiter;
import com.applypilot.common.ai.ChatCompletion;
import com.applypilot.common.ai.ChatCompletionClient;
import com.applypilot.common.ai.ChatCompletionRequest;
import com.applypilot.common.ai.ConversationRequest;
import com.applypilot.common.ai.LlmException;
import com.applypilot.common.ai.LlmRateLimitedException;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

/**
 * The agents' only way to the model. Every call first takes one of the user's daily AI calls
 * (the same counter the job posting analysis uses), so the limit holds across all agents,
 * and every call is recorded on the step's {@link StepMeter}. Provider failures become
 * {@link AgentStepFailedException} with a reason the orchestrator can act on.
 */
@Component
public class AgentModel {

	private final ChatCompletionClient client;

	private final AiUsageLimiter usageLimiter;

	public AgentModel(ChatCompletionClient client, AiUsageLimiter usageLimiter) {
		this.client = client;
		this.usageLimiter = usageLimiter;
	}

	public boolean isConfigured() {
		return client.isConfigured();
	}

	public ChatCompletion complete(StepMeter meter, ChatCompletionRequest request) {
		return call(meter, () -> client.complete(request));
	}

	public ChatCompletion converse(StepMeter meter, ConversationRequest request) {
		return call(meter, () -> client.converse(request));
	}

	private ChatCompletion call(StepMeter meter, Supplier<ChatCompletion> request) {
		if (!client.isConfigured()) {
			throw new AgentStepFailedException(StepReason.NOT_CONFIGURED, "No AI provider is configured");
		}
		try {
			usageLimiter.acquire(meter.userId());
		}
		catch (AiDailyLimitReachedException ex) {
			throw new AgentStepFailedException(StepReason.DAILY_LIMIT_REACHED, ex.getMessage());
		}
		try {
			ChatCompletion completion = request.get();
			meter.recordModelCall(completion);
			return completion;
		}
		catch (LlmRateLimitedException ex) {
			throw new AgentStepFailedException(StepReason.RATE_LIMITED, ex.getMessage());
		}
		catch (LlmException ex) {
			throw new AgentStepFailedException(StepReason.PROVIDER_ERROR, ex.getMessage());
		}
	}

}
