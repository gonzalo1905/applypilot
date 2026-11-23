package com.applypilot.agent;

/** Life cycle of an agent run. */
public enum RunStatus {

	/** Accepted and waiting for a worker thread. */
	PENDING,

	RUNNING,

	/** Match result and cover letter are ready. */
	SUCCEEDED,

	/** The match result is ready but no cover letter could be written. */
	PARTIAL,

	/** Nothing usable was produced; {@code errorCode} says why. */
	FAILED;

	public boolean isActive() {
		return this == PENDING || this == RUNNING;
	}

}
