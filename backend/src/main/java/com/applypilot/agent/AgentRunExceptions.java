package com.applypilot.agent;

import com.applypilot.common.error.ApiException;
import org.springframework.http.HttpStatus;

/** The API errors of agent runs, each with a stable code. */
public final class AgentRunExceptions {

	private AgentRunExceptions() {
	}

	public static class AgentRunNotFoundException extends ApiException {

		public AgentRunNotFoundException() {
			super(HttpStatus.NOT_FOUND, "AGENT_RUN_NOT_FOUND", "This application has no match run yet");
		}

	}

	public static class CvRequiredException extends ApiException {

		public CvRequiredException() {
			super(HttpStatus.CONFLICT, "CV_REQUIRED", "Upload your CV in your profile before matching a job");
		}

	}

	public static class AnalysisRequiredException extends ApiException {

		public AnalysisRequiredException() {
			super(HttpStatus.CONFLICT, "ANALYSIS_REQUIRED",
					"Analyze the job posting first, or send its text with the request");
		}

	}

	public static class NotEnoughAiCallsException extends ApiException {

		public NotEnoughAiCallsException(int remaining, int needed) {
			super(HttpStatus.TOO_MANY_REQUESTS, "NOT_ENOUGH_AI_CALLS", "A match run needs at least " + needed
					+ " AI calls and you have " + remaining + " left today. Try again tomorrow.");
		}

	}

	public static class AgentRunsBusyException extends ApiException {

		public AgentRunsBusyException() {
			super(HttpStatus.SERVICE_UNAVAILABLE, "AGENT_RUNS_BUSY",
					"Too many match runs are waiting. Try again in a minute.");
		}

	}

}
