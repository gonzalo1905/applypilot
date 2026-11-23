package com.applypilot.common.ai;

import com.openai.errors.RateLimitException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel.ResponseFormat;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * {@link ChatCompletionClient} backed by Spring AI's OpenAI-compatible chat model, which is
 * pointed at Groq through {@code spring.ai.openai.base-url}. This is the only class that
 * knows about Spring AI or the provider SDK.
 */
@Component
@EnableConfigurationProperties(AiProperties.class)
public class SpringAiChatCompletionClient implements ChatCompletionClient {

	/** Value of the API key property when no real key was provided. */
	static final String NO_KEY = "not-configured";

	private static final Logger log = LoggerFactory.getLogger(SpringAiChatCompletionClient.class);

	private final ChatModel chatModel;

	private final AiProperties properties;

	private final boolean configured;

	public SpringAiChatCompletionClient(ChatModel chatModel, AiProperties properties,
			@Value("${spring.ai.openai.api-key:" + NO_KEY + "}") String apiKey) {
		this.chatModel = chatModel;
		this.properties = properties;
		this.configured = apiKey != null && !apiKey.isBlank() && !NO_KEY.equals(apiKey);
	}

	@Override
	public boolean isConfigured() {
		return configured;
	}

	@Override
	public ChatCompletion complete(ChatCompletionRequest request) {
		OpenAiChatOptions.Builder options = OpenAiChatOptions.builder();
		options.model(properties.model());
		options.temperature(properties.temperature());
		options.maxCompletionTokens(properties.maxCompletionTokens());
		// Structured output: the provider constrains the model to this schema ("strict").
		options.responseFormat(ResponseFormat.builder()
			.type(ResponseFormat.Type.JSON_SCHEMA)
			.jsonSchema(request.jsonSchema())
			.strict(true)
			.build());
		if (properties.reasoningEffort() != null && !properties.reasoningEffort().isBlank()) {
			options.reasoningEffort(properties.reasoningEffort());
		}
		// The system message carries our rules; the user message carries the (untrusted) input.
		Prompt prompt = new Prompt(
				List.of(new SystemMessage(request.systemPrompt()), new UserMessage(request.userPrompt())),
				options.build());

		long start = System.nanoTime();
		try {
			ChatResponse response = chatModel.call(prompt);
			Usage usage = response.getMetadata().getUsage();
			ChatCompletion completion = new ChatCompletion(response.getResult().getOutput().getText(),
					response.getMetadata().getModel(), usage != null ? usage.getPromptTokens() : null,
					usage != null ? usage.getCompletionTokens() : null);
			// Cost and latency are logged; prompts and answers never are (they hold user data).
			log.info("LLM call ok: model={} latencyMs={} promptTokens={} completionTokens={}", completion.model(),
					elapsedMillis(start), completion.promptTokens(), completion.completionTokens());
			return completion;
		}
		catch (RateLimitException ex) {
			log.warn("LLM call rate limited after {} ms", elapsedMillis(start));
			throw new LlmRateLimitedException(ex);
		}
		catch (RuntimeException ex) {
			log.warn("LLM call failed after {} ms: {}", elapsedMillis(start), ex.getClass().getSimpleName());
			throw new LlmUnavailableException("The AI provider request failed", ex);
		}
	}

	private static long elapsedMillis(long startNanos) {
		return (System.nanoTime() - startNanos) / 1_000_000;
	}

}
