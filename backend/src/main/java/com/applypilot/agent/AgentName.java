package com.applypilot.agent;

/** The members of the agent team, in the order the orchestrator runs them. */
public enum AgentName {

	/** Turns the job posting into structured data (Stage 3, reused here). */
	EXTRACTOR,

	/** Compares the posting's skills with the CV, using tools to look facts up. */
	MATCHER,

	/** Drafts the cover letter. */
	WRITER,

	/** Checks the letter and sends it back to the Writer when needed. */
	REVIEWER

}
