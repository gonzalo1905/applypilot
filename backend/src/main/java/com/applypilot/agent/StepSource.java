package com.applypilot.agent;

/** Who produced a step's output. */
public enum StepSource {

	/** The language model. */
	LLM,

	/** Deterministic code, used when the model was not available or not trustworthy. */
	FALLBACK,

	/** An earlier result was reused, at no cost (for example an existing analysis). */
	REUSED,

	/** The step did not run, because an earlier one gave it nothing to work on. */
	SKIPPED

}
