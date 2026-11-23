package com.applypilot.common.ai;

/** The provider answered 429: too many requests or the token quota is used up. */
public class LlmRateLimitedException extends LlmException {

	public LlmRateLimitedException(Throwable cause) {
		super("The AI provider rate limit was reached", cause);
	}

}
