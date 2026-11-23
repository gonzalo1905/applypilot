package com.applypilot.common.ai;

/**
 * The only door between this application and a large language model.
 *
 * <p>Features depend on this interface, never on a provider SDK. That keeps the provider
 * swappable (Groq today; any OpenAI-compatible API through configuration), lets tests replace
 * the model with a fake that costs nothing and always answers the same, and puts timeouts and
 * error mapping in one place.
 */
public interface ChatCompletionClient {

	/** False when no API key is configured; callers then use their non-AI fallback. */
	boolean isConfigured();

	/**
	 * Sends one request and returns the model's answer.
	 *
	 * @throws LlmRateLimitedException if the provider answered 429 (quota or rate limit)
	 * @throws LlmUnavailableException for any other failure: timeout, network, provider error
	 */
	ChatCompletion complete(ChatCompletionRequest request);

}
