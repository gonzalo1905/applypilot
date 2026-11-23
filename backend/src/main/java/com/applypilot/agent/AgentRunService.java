package com.applypilot.agent;

import com.applypilot.agent.AgentRunExceptions.AgentRunNotFoundException;
import com.applypilot.agent.AgentRunExceptions.AgentRunsBusyException;
import com.applypilot.agent.AgentRunExceptions.AnalysisRequiredException;
import com.applypilot.agent.AgentRunExceptions.CvRequiredException;
import com.applypilot.agent.dto.AgentRunResponse;
import com.applypilot.agent.dto.StartAgentRunRequest;
import com.applypilot.analysis.AiDailyLimitReachedException;
import com.applypilot.analysis.AiUsageLimiter;
import com.applypilot.analysis.JobAnalysisRepository;
import com.applypilot.application.ApplicationNotFoundException;
import com.applypilot.application.JobApplicationRepository;
import com.applypilot.common.ai.AiProperties;
import com.applypilot.cv.CvRepository;
import java.time.Clock;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Starts match runs and reads them back. Starting only checks the preconditions and queues
 * the run; the work happens on {@link AgentRunExecutor} and the client polls
 * {@link #latest}.
 */
@Service
public class AgentRunService {

	private static final Set<RunStatus> ACTIVE = EnumSet.of(RunStatus.PENDING, RunStatus.RUNNING);

	private final AgentRunRepository runs;

	private final AgentRunStepRepository steps;

	private final JobApplicationRepository applications;

	private final JobAnalysisRepository analyses;

	private final CvRepository cvs;

	private final AgentModel model;

	private final AiUsageLimiter usageLimiter;

	private final AiProperties aiProperties;

	private final AgentRunOrchestrator orchestrator;

	private final AgentRunExecutor executor;

	private final AgentRunRecorder recorder;

	private final JsonAnswers json;

	private final Clock clock;

	private final TransactionTemplate transaction;

	public AgentRunService(AgentRunRepository runs, AgentRunStepRepository steps,
			JobApplicationRepository applications, JobAnalysisRepository analyses, CvRepository cvs, AgentModel model,
			AiUsageLimiter usageLimiter, AiProperties aiProperties, AgentRunOrchestrator orchestrator,
			AgentRunExecutor executor, AgentRunRecorder recorder, JsonAnswers json, Clock clock,
			TransactionTemplate transaction) {
		this.runs = runs;
		this.steps = steps;
		this.applications = applications;
		this.analyses = analyses;
		this.cvs = cvs;
		this.model = model;
		this.usageLimiter = usageLimiter;
		this.aiProperties = aiProperties;
		this.orchestrator = orchestrator;
		this.executor = executor;
		this.recorder = recorder;
		this.json = json;
		this.clock = clock;
		this.transaction = transaction;
	}

	/**
	 * Queues a run for the application. If one is already queued or running, that one is
	 * returned instead of starting a second: a double click must not cost twice.
	 *
	 * @throws CvRequiredException 409 when the user has no CV
	 * @throws AnalysisRequiredException 409 when there is neither a stored analysis nor text
	 * @throws AiDailyLimitReachedException 429 when the model is configured and no call is
	 * left today (without a model, runs use their fallbacks and cost nothing)
	 * @throws AgentRunsBusyException 503 when the queue is full
	 */
	public AgentRunResponse start(UUID userId, UUID applicationId, StartAgentRunRequest request) {
		String postingText = request == null || request.postingText() == null || request.postingText().isBlank()
				? null : request.postingText();
		Started started = transaction.execute(status -> {
			// Serializes starts of the same user, so two clicks cannot create two runs.
			applications.lockBoard(userId);
			requireOwnedApplication(userId, applicationId);
			var active = runs.findFirstByApplicationIdAndUserIdAndStatusIn(applicationId, userId, ACTIVE);
			if (active.isPresent()) {
				return new Started(active.get().getId(), false);
			}
			if (cvs.findByUserId(userId).isEmpty()) {
				throw new CvRequiredException();
			}
			if (postingText == null && analyses.findByApplicationIdAndUserId(applicationId, userId).isEmpty()) {
				throw new AnalysisRequiredException();
			}
			if (model.isConfigured() && usageLimiter.remaining(userId) == 0) {
				throw new AiDailyLimitReachedException(aiProperties.dailyLimit());
			}
			return new Started(runs.save(new AgentRun(applicationId, userId, clock.instant())).getId(), true);
		});
		if (started.isNew()) {
			try {
				executor.submit(() -> orchestrator.execute(started.runId(), postingText));
			}
			catch (RejectedExecutionException ex) {
				recorder.finish(started.runId(), RunStatus.FAILED, "AGENT_RUNS_BUSY");
				throw new AgentRunsBusyException();
			}
		}
		return transaction.execute(status -> response(runs.findById(started.runId()).orElseThrow()));
	}

	/** The application's most recent run, finished or not. */
	public AgentRunResponse latest(UUID userId, UUID applicationId) {
		return transaction.execute(status -> {
			requireOwnedApplication(userId, applicationId);
			return runs.findFirstByApplicationIdAndUserIdOrderByCreatedAtDesc(applicationId, userId)
				.map(this::response)
				.orElseThrow(AgentRunNotFoundException::new);
		});
	}

	private AgentRunResponse response(AgentRun run) {
		return AgentRunResponse.from(run, steps.findByRunIdOrderByStepOrder(run.getId()), json);
	}

	private void requireOwnedApplication(UUID userId, UUID applicationId) {
		if (applications.findByIdAndUserId(applicationId, userId).isEmpty()) {
			throw new ApplicationNotFoundException();
		}
	}

	private record Started(UUID runId, boolean isNew) {
	}

}
