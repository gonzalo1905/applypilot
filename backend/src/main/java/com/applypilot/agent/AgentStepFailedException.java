package com.applypilot.agent;

/**
 * An agent could not produce a trustworthy answer. Not an API error: the orchestrator
 * catches it and degrades (a deterministic fallback, or a run without a letter).
 */
public class AgentStepFailedException extends RuntimeException {

	private final StepReason reason;

	public AgentStepFailedException(StepReason reason, String message) {
		super(message);
		this.reason = reason;
	}

	public StepReason getReason() {
		return reason;
	}

}
