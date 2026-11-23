package com.applypilot.analysis;

/**
 * Why the keyword extractor was used instead of the language model.
 */
public enum FallbackReason {

	/** No API key is configured. */
	NOT_CONFIGURED,

	/** The provider answered 429 (its rate limit or quota). */
	RATE_LIMITED,

	/** Timeout, network failure or a provider error. */
	PROVIDER_ERROR,

	/** The model answered twice with output that did not match the schema. */
	INVALID_OUTPUT

}
