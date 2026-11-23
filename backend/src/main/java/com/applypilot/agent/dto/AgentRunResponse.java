package com.applypilot.agent.dto;

import com.applypilot.agent.AgentName;
import com.applypilot.agent.AgentRun;
import com.applypilot.agent.AgentRunStep;
import com.applypilot.agent.JsonAnswers;
import com.applypilot.agent.MatchResult;
import com.applypilot.agent.Review;
import com.applypilot.agent.RunStatus;
import com.applypilot.agent.StepReason;
import com.applypilot.agent.StepSource;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A match run as the client sees it: status for polling, the results so far, and the step
 * timeline. The steps' stored outputs are not included; they stay in the database for
 * tracing.
 *
 * @param currentAgent the agent working now; null when the run is not running
 * @param match available as soon as the Matcher has finished, even while the letter is
 * being written
 * @param coverLetter null until the run succeeds, and on PARTIAL or FAILED runs
 */
public record AgentRunResponse(
		UUID id,
		UUID applicationId,
		RunStatus status,
		AgentName currentAgent,
		String errorCode,
		MatchResult match,
		String coverLetter,
		String letterLanguage,
		Review review,
		int revisions,
		List<Step> steps,
		Instant createdAt,
		Instant startedAt,
		Instant finishedAt) {

	public static AgentRunResponse from(AgentRun run, List<AgentRunStep> steps, JsonAnswers json) {
		return new AgentRunResponse(run.getId(), run.getApplicationId(), run.getStatus(), run.getCurrentAgent(),
				run.getErrorCode(), json.read(run.getMatchResult(), MatchResult.class), run.getCoverLetter(),
				run.getLetterLanguage(), json.read(run.getReview(), Review.class), run.getRevisions(),
				steps.stream().map(Step::from).toList(), run.getCreatedAt(), run.getStartedAt(), run.getFinishedAt());
	}

	/** One entry of the run's timeline. */
	public record Step(
			int order,
			AgentName agent,
			int attempt,
			StepSource source,
			StepReason reason,
			String model,
			int modelCalls,
			int toolCalls,
			Integer promptTokens,
			Integer completionTokens,
			long latencyMs,
			String inputHash,
			Instant startedAt) {

		static Step from(AgentRunStep step) {
			return new Step(step.getStepOrder(), step.getAgent(), step.getAttempt(), step.getSource(),
					step.getReason(), step.getModel(), step.getModelCalls(), step.getToolCalls(),
					step.getPromptTokens(), step.getCompletionTokens(), step.getLatencyMs(), step.getInputHash(),
					step.getStartedAt());
		}

	}

}
