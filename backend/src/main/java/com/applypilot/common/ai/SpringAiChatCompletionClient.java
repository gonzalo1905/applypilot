package com.applypilot.common.ai;

import com.openai.errors.RateLimitException;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel.ResponseFormat;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
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
		OpenAiChatOptions.Builder options = baseOptions();
		// Structured output: the provider constrains the model to this schema ("strict").
		options.responseFormat(ResponseFormat.builder()
			.type(ResponseFormat.Type.JSON_SCHEMA)
			.jsonSchema(request.jsonSchema())
			.strict(true)
			.build());
		// The system message carries our rules; the user message carries the (untrusted) input.
		return call(new Prompt(
				List.of(new SystemMessage(request.systemPrompt()), new UserMessage(request.userPrompt())),
				options.build()));
	}

	/**
	 * Tool calling where our code runs the loop: the tools are only described to the model.
	 * In Spring AI 2 the chat model itself does not execute tools (that is the job of
	 * {@code ChatClient} advisors, which we do not use), so a tool call comes back to the
	 * caller as data. If that ever changed, {@link DescribedTool#call} would fail loudly
	 * instead of running anything.
	 */
	@Override
	public ChatCompletion converse(ConversationRequest request) {
		OpenAiChatOptions.Builder options = baseOptions();
		if (!request.tools().isEmpty()) {
			options.toolCallbacks(request.tools().stream().map(DescribedTool::new).map(ToolCallback.class::cast).toList());
		}
		List<Message> messages = new ArrayList<>();
		messages.add(new SystemMessage(request.systemPrompt()));
		for (ConversationMessage message : request.messages()) {
			messages.add(toSpringAi(message));
		}
		return call(new Prompt(messages, options.build()));
	}

	private OpenAiChatOptions.Builder baseOptions() {
		OpenAiChatOptions.Builder options = OpenAiChatOptions.builder();
		options.model(properties.model());
		options.temperature(properties.temperature());
		options.maxCompletionTokens(properties.maxCompletionTokens());
		if (properties.reasoningEffort() != null && !properties.reasoningEffort().isBlank()) {
			options.reasoningEffort(properties.reasoningEffort());
		}
		return options;
	}

	private static Message toSpringAi(ConversationMessage message) {
		return switch (message) {
			case ConversationMessage.User user -> new UserMessage(user.text());
			case ConversationMessage.Assistant assistant -> AssistantMessage.builder()
				.content(assistant.text() == null ? "" : assistant.text())
				.toolCalls(assistant.toolCalls()
					.stream()
					.map(call -> new AssistantMessage.ToolCall(call.id(), "function", call.name(), call.arguments()))
					.toList())
				.build();
			case ConversationMessage.ToolResult result -> ToolResponseMessage.builder()
				.responses(List.of(new ToolResponseMessage.ToolResponse(result.toolCallId(), result.toolName(),
						result.content())))
				.build();
		};
	}

	private ChatCompletion call(Prompt prompt) {
		long start = System.nanoTime();
		try {
			ChatResponse response = chatModel.call(prompt);
			Usage usage = response.getMetadata().getUsage();
			AssistantMessage output = response.getResult().getOutput();
			List<ToolCall> toolCalls = output.getToolCalls() == null ? List.of()
					: output.getToolCalls()
						.stream()
						.map(call -> new ToolCall(call.id(), call.name(), call.arguments()))
						.toList();
			ChatCompletion completion = new ChatCompletion(output.getText(), response.getMetadata().getModel(),
					usage != null ? usage.getPromptTokens() : null, usage != null ? usage.getCompletionTokens() : null,
					toolCalls);
			// Cost and latency are logged; prompts and answers never are (they hold user data).
			log.info("LLM call ok: model={} latencyMs={} promptTokens={} completionTokens={} toolCalls={}",
					completion.model(), elapsedMillis(start), completion.promptTokens(), completion.completionTokens(),
					toolCalls.size());
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

	/**
	 * Gives the model a tool's name, description and argument schema. It is never executed
	 * here: running it inside the SDK would bypass the caller's checks and limits.
	 */
	private record DescribedTool(ToolSpec spec) implements ToolCallback {

		@Override
		public ToolDefinition getToolDefinition() {
			return ToolDefinition.builder()
				.name(spec.name())
				.description(spec.description())
				.inputSchema(spec.parametersSchema())
				.build();
		}

		@Override
		public String call(String toolInput) {
			throw new UnsupportedOperationException("Tools are executed by the caller, not by Spring AI");
		}

	}

	private static long elapsedMillis(long startNanos) {
		return (System.nanoTime() - startNanos) / 1_000_000;
	}

}
