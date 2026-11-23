package com.applypilot.common.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;

/** The Spring AI chat model is mocked: these tests check our mapping, not the provider. */
class SpringAiChatCompletionClientTest {

	private static final AiProperties PROPERTIES = new AiProperties("openai/gpt-oss-120b", 0, 2000, "low", 20);

	private final ChatModel chatModel = mock(ChatModel.class);

	@Test
	void isConfiguredOnlyWithARealKey() {
		assertThat(client(SpringAiChatCompletionClient.NO_KEY).isConfigured()).isFalse();
		assertThat(client("  ").isConfigured()).isFalse();
		assertThat(client(null).isConfigured()).isFalse();
		assertThat(client("gsk_test").isConfigured()).isTrue();
	}

	@Test
	void sendsSystemAndUserMessagesWithTheConfiguredModelAndTemperatureZero() {
		ChatResponse response = mock(ChatResponse.class, RETURNS_DEEP_STUBS);
		when(response.getResult().getOutput().getText()).thenReturn("{\"ok\": true}");
		when(response.getMetadata().getModel()).thenReturn("openai/gpt-oss-120b");
		when(response.getMetadata().getUsage().getPromptTokens()).thenReturn(812);
		when(response.getMetadata().getUsage().getCompletionTokens()).thenReturn(143);
		when(chatModel.call(any(Prompt.class))).thenReturn(response);

		ChatCompletion completion = client("gsk_test")
			.complete(new ChatCompletionRequest("system rules", "untrusted posting", "{\"type\": \"object\"}"));

		assertThat(completion).isEqualTo(new ChatCompletion("{\"ok\": true}", "openai/gpt-oss-120b", 812, 143));
		ArgumentCaptor<Prompt> sent = ArgumentCaptor.forClass(Prompt.class);
		verify(chatModel).call(sent.capture());
		Prompt prompt = sent.getValue();
		assertThat(prompt.getInstructions()).extracting(message -> message.getMessageType())
			.containsExactly(MessageType.SYSTEM, MessageType.USER);
		assertThat(prompt.getInstructions()).extracting(message -> message.getText())
			.containsExactly("system rules", "untrusted posting");
		OpenAiChatOptions options = (OpenAiChatOptions) prompt.getOptions();
		assertThat(options.getModel()).isEqualTo("openai/gpt-oss-120b");
		assertThat(options.getTemperature()).isZero();
	}

	@Test
	void turnsAnyProviderFailureIntoLlmUnavailable() {
		when(chatModel.call(any(Prompt.class))).thenThrow(new IllegalStateException("connect timed out"));

		assertThatThrownBy(() -> client("gsk_test").complete(new ChatCompletionRequest("s", "u", "{}")))
			.isInstanceOf(LlmUnavailableException.class)
			.hasCauseInstanceOf(IllegalStateException.class);
	}

	private SpringAiChatCompletionClient client(String apiKey) {
		return new SpringAiChatCompletionClient(chatModel, PROPERTIES, apiKey);
	}

}
