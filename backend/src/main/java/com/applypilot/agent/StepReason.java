package com.applypilot.agent;

/** Why a step used a fallback or was skipped. */
public enum StepReason {

	/** No API key is configured. */
	NOT_CONFIGURED,

	/** The provider answered 429 (its rate limit or quota). */
	RATE_LIMITED,

	/** Timeout, network failure or a provider error. */
	PROVIDER_ERROR,

	/** The model's answer failed validation twice. */
	INVALID_OUTPUT,

	/** The user has used today's AI calls. */
	DAILY_LIMIT_REACHED,

	/** An earlier step produced nothing this step could use. */
	PREVIOUS_STEP_FAILED

}
