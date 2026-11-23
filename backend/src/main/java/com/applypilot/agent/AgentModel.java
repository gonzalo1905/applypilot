package com.applypilot.agent;

import com.applypilot.analysis.AiDailyLimitReachedException;
import com.applypilot.analysis.AiUsageLimiter;
import com.applypilot.common.ai.ChatCompletion;
import com.applypilot.common.ai.ChatCompletionClient;
import com.applypilot.common.ai.ChatCompletionRequest;
import com.applypilot.common.ai.ConversationRequest;
import com.applypilot.common.ai.LlmException;
import com.applypilot.common.ai.LlmRateLimitedException;
import java.util.function.Function;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * The agents' only way to the model. Every call first takes one of the user's daily AI calls
 * (the same counter the job posting analysis uses), so the limit holds across all agents,
 * and every call is recorded on the step's {@link StepMeter}. Provider failures become
 * {@link AgentStepFailedException} with a reason the orchestrator can act on.
 */
@Component
public class AgentModel {

	private static final Logger log = LoggerFactory.getLogger(AgentModel.class);

	private final ChatCompletionClient client;

	private final AiUsageLimiter usageLimiter;

	private final JsonAnswers answers;

	public AgentModel(ChatCompletionClient client, AiUsageLimiter usageLimiter, JsonAnswers answers) {
		this.client = client;
		this.usageLimiter = usageLimiter;
		this.answers = answers;
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

	/**
	 * One structured call whose answer must parse into {@code type} and pass validation. An
	 * invalid answer gets one retry that says what was wrong; a second invalid answer throws
	 * {@link AgentStepFailedException} with {@link StepReason#INVALID_OUTPUT}.
	 */
	public <T> T completeValidated(StepMeter meter, ChatCompletionRequest request, Class<T> type) {
		return completeValidated(meter, request, type, value -> null);
	}

	/**
	 * Same, with an extra rule checked in code after validation; breaking it costs the retry
	 * like any other invalid answer.
	 *
	 * @param extraCheck returns what is wrong with a valid value, or null when it is fine
	 */
	public <T> T completeValidated(StepMeter meter, ChatCompletionRequest request, Class<T> type,
			Function<T, String> extraCheck) {
		JsonAnswers.Parsed<T> first = check(answers.parse(complete(meter, request).content(), type), extraCheck);
		if (first.isValid()) {
			return first.value();
		}
		log.warn("{} rejected, retrying once: {}", type.getSimpleName(), first.problem());
		ChatCompletionRequest retry = new ChatCompletionRequest(request.systemPrompt(),
				request.userPrompt() + "\n\nYour previous answer was rejected: " + first.problem()
						+ ". Answer again with one JSON object that matches the schema and nothing else.",
				request.jsonSchema());
		JsonAnswers.Parsed<T> second = check(answers.parse(complete(meter, retry).content(), type), extraCheck);
		if (second.isValid()) {
			return second.value();
		}
		throw new AgentStepFailedException(StepReason.INVALID_OUTPUT,
				type.getSimpleName() + " was invalid twice: " + second.problem());
	}

	private static <T> JsonAnswers.Parsed<T> check(JsonAnswers.Parsed<T> parsed, Function<T, String> extraCheck) {
		if (!parsed.isValid()) {
			return parsed;
		}
		String problem = extraCheck.apply(parsed.value());
		return problem == null ? parsed : new JsonAnswers.Parsed<>(null, problem);
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
