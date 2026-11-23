package com.applypilot.agent;

import com.applypilot.agent.AgentRunExceptions.CvRequiredException;
import com.applypilot.cv.Cv;
import com.applypilot.cv.CvRepository;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The orchestrator's short database transactions. Model calls happen between them, never
 * inside one, so no connection or lock is held while a model thinks. Each method commits on
 * its own, which also makes progress visible to a polling client right away.
 *
 * <p>If the application was deleted during the run, its run row is gone (cascade) and these
 * updates do nothing.
 */
@Component
public class AgentRunRecorder {

	private final AgentRunRepository runs;

	private final AgentRunStepRepository steps;

	private final CvRepository cvs;

	private final Clock clock;

	public AgentRunRecorder(AgentRunRepository runs, AgentRunStepRepository steps, CvRepository cvs, Clock clock) {
		this.runs = runs;
		this.steps = steps;
		this.clock = clock;
		this.cvs = cvs;
	}

	/** Moves a queued run to RUNNING; null if it no longer exists or was already started. */
	@Transactional
	public AgentRun start(UUID runId) {
		return runs.findById(runId).filter(run -> run.getStatus() == RunStatus.PENDING).map(run -> {
			run.start(clock.instant());
			return run;
		}).orElse(null);
	}

	@Transactional
	public void working(UUID runId, AgentName agent) {
		runs.findById(runId).ifPresent(run -> run.working(agent));
	}

	@Transactional
	public void step(UUID runId, int order, AgentName agent, int attempt, StepRecord record) {
		if (runs.existsById(runId)) {
			steps.save(new AgentRunStep(runId, order, agent, attempt, record));
		}
	}

	@Transactional
	public void matched(UUID runId, String matchJson) {
		runs.findById(runId).ifPresent(run -> run.matched(matchJson));
	}

	@Transactional
	public void lettered(UUID runId, CoverLetterDraft draft, String reviewJson, int revisions) {
		runs.findById(runId).ifPresent(run -> run.lettered(draft.letter(), draft.language(), reviewJson, revisions));
	}

	@Transactional
	public void finish(UUID runId, RunStatus status, String errorCode) {
		runs.findById(runId).ifPresent(run -> run.finish(status, errorCode, clock.instant()));
	}

	/** The CV text of the run's user. Loaded by the user id, so only the owner's CV is read. */
	@Transactional(readOnly = true)
	public String cvText(UUID userId) {
		return cvs.findByUserId(userId).map(Cv::getExtractedText).orElseThrow(CvRequiredException::new);
	}

}
