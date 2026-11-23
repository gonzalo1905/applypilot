package com.applypilot.agent;

import com.applypilot.analysis.AnalysisService;
import com.applypilot.analysis.AnalysisSource;
import com.applypilot.analysis.ExtractedPosting;
import com.applypilot.analysis.dto.AnalysisResponse;
import com.applypilot.analysis.dto.AnalyzeRequest;
import com.applypilot.common.error.ApiException;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Runs the agent team for one run: Extractor, then Matcher, then Writer and Reviewer in an
 * evaluator-optimizer loop.
 *
 * <p>The agents never talk to each other. Each one receives structured data from this class
 * and returns a validated record; this class decides what runs next. That keeps every hand-off
 * inspectable, stops text produced by one model from steering another, and lets any agent be
 * replaced by a fallback without the others noticing.
 *
 * <p>Every step is recorded (input hash, output, source, reason, model, calls, tokens,
 * latency), so a run can be traced afterwards without logging user data. The daily AI limit
 * applies to every model call of every agent ({@link AgentModel}).
 *
 * <p>Degrading instead of failing: the Matcher falls back to keywords; the Reviewer falls
 * back to code-only checks; if the Writer is unavailable, the run still delivers the match
 * result (status PARTIAL).
 */
@Component
public class AgentRunOrchestrator {

	/** Writer revisions after the first draft; at most three drafts and three reviews. */
	static final int MAX_REVISIONS = 2;

	private static final Logger log = LoggerFactory.getLogger(AgentRunOrchestrator.class);

	private final AgentRunRecorder recorder;

	private final AnalysisService analysisService;

	private final MatcherAgent matcher;

	private final KeywordSkillMatcher keywordMatcher;

	private final WriterAgent writer;

	private final ReviewerAgent reviewer;

	private final JsonAnswers json;

	private final Clock clock;

	public AgentRunOrchestrator(AgentRunRecorder recorder, AnalysisService analysisService, MatcherAgent matcher,
			KeywordSkillMatcher keywordMatcher, WriterAgent writer, ReviewerAgent reviewer, JsonAnswers json,
			Clock clock) {
		this.recorder = recorder;
		this.analysisService = analysisService;
		this.matcher = matcher;
		this.keywordMatcher = keywordMatcher;
		this.writer = writer;
		this.reviewer = reviewer;
		this.json = json;
		this.clock = clock;
	}

	/**
	 * @param postingText new posting text to analyze first, or null to reuse the stored
	 * analysis
	 */
	public void execute(UUID runId, String postingText) {
		AgentRun run = recorder.start(runId);
		if (run == null) {
			return;
		}
		Trace trace = new Trace(runId, run.getUserId());
		try {
			AnalysisResponse analysis = extract(trace, run.getApplicationId(), postingText);
			ExtractedPosting posting = toPosting(analysis);
			String cvText = recorder.cvText(run.getUserId());

			MatchResult match = match(trace, posting, cvText);
			recorder.matched(runId, json.write(match));

			Letter letter = writeAndReview(trace, posting, analysis.postingText(), cvText, match);
			if (letter == null) {
				recorder.finish(runId, RunStatus.PARTIAL, null);
				return;
			}
			recorder.lettered(runId, letter.draft(), json.write(letter.review()), letter.revisions());
			recorder.finish(runId, RunStatus.SUCCEEDED, null);
		}
		catch (ApiException ex) {
			// Expected problems (no CV, no analysis, daily limit during extraction...).
			recorder.finish(runId, RunStatus.FAILED, ex.getCode());
		}
		catch (RuntimeException ex) {
			log.error("Agent run {} failed: {}", runId, ex.getClass().getSimpleName(), ex);
			recorder.finish(runId, RunStatus.FAILED, "INTERNAL_ERROR");
		}
	}

	/** Reuses the stored analysis, or analyzes new text with the Stage 3 extractor. */
	private AnalysisResponse extract(Trace trace, UUID applicationId, String postingText) {
		recorder.working(trace.runId(), AgentName.EXTRACTOR);
		Instant started = clock.instant();
		long start = System.nanoTime();
		AnalysisResponse analysis;
		StepSource source;
		StepReason reason = null;
		int modelCalls = 0;
		if (postingText == null) {
			analysis = analysisService.get(trace.userId(), applicationId);
			source = StepSource.REUSED;
		}
		else {
			analysis = analysisService.analyze(trace.userId(), applicationId, new AnalyzeRequest(postingText));
			if (analysis.cached()) {
				source = StepSource.REUSED;
			}
			else if (analysis.source() == AnalysisSource.LLM) {
				source = StepSource.LLM;
				modelCalls = 1;
			}
			else {
				source = StepSource.FALLBACK;
				reason = StepReason.valueOf(analysis.fallbackReason().name());
			}
		}
		// The analysis service does not report tokens; it counts as one call of the daily limit.
		trace.record(AgentName.EXTRACTOR, 1, source, reason, json.hash(analysis.postingText()), toPosting(analysis),
				new StepUsage(analysis.model(), modelCalls, 0, null, null), started, start);
		return analysis;
	}

	private MatchResult match(Trace trace, ExtractedPosting posting, String cvText) {
		recorder.working(trace.runId(), AgentName.MATCHER);
		Instant started = clock.instant();
		long start = System.nanoTime();
		StepMeter meter = new StepMeter(trace.userId());
		String inputHash = json.hash(List.of(posting.requiredSkills(), posting.niceToHaveSkills(), cvText));
		try {
			MatchResult match = matcher.match(meter, posting, cvText);
			trace.record(AgentName.MATCHER, 1, StepSource.LLM, null, inputHash, match, meter.usage(), started, start);
			return match;
		}
		catch (AgentStepFailedException ex) {
			log.warn("Matcher fell back to keywords: reason={}", ex.getReason());
			MatchResult match = keywordMatcher.match(posting, cvText);
			trace.record(AgentName.MATCHER, 1, StepSource.FALLBACK, ex.getReason(), inputHash, match, meter.usage(),
					started, start);
			return match;
		}
	}

	/**
	 * The evaluator-optimizer loop: the Writer drafts, the Reviewer judges, and an unapproved
	 * draft goes back to the Writer with the feedback, at most {@value #MAX_REVISIONS} times.
	 * A failed revision keeps the last good draft.
	 */
	private Letter writeAndReview(Trace trace, ExtractedPosting posting, String postingText, String cvText,
			MatchResult match) {
		CoverLetterDraft draft = write(trace, 1, posting, postingText, cvText, match, null, null);
		if (draft == null) {
			trace.record(AgentName.REVIEWER, 1, StepSource.SKIPPED, StepReason.PREVIOUS_STEP_FAILED,
					json.hash(""), null, StepUsage.NONE, clock.instant(), System.nanoTime());
			return null;
		}
		Review review = review(trace, 1, posting, postingText, cvText, match, draft);
		int revisions = 0;
		while (!review.approved() && revisions < MAX_REVISIONS) {
			CoverLetterDraft revised = write(trace, revisions + 2, posting, postingText, cvText, match, draft, review);
			if (revised == null) {
				break;
			}
			revisions++;
			draft = revised;
			review = review(trace, revisions + 1, posting, postingText, cvText, match, draft);
		}
		return new Letter(draft, review, revisions);
	}

	/** One Writer step; null when the model could not write (recorded as SKIPPED). */
	private CoverLetterDraft write(Trace trace, int attempt, ExtractedPosting posting, String postingText,
			String cvText, MatchResult match, CoverLetterDraft previous, Review feedback) {
		recorder.working(trace.runId(), AgentName.WRITER);
		Instant started = clock.instant();
		long start = System.nanoTime();
		StepMeter meter = new StepMeter(trace.userId());
		Map<String, Object> input = new LinkedHashMap<>();
		input.put("posting", postingText);
		input.put("cv", cvText);
		input.put("match", match);
		input.put("previous", previous);
		input.put("feedback", feedback);
		String inputHash = json.hash(input);
		try {
			CoverLetterDraft draft = writer.write(meter, posting, postingText, cvText, match, previous, feedback);
			trace.record(AgentName.WRITER, attempt, StepSource.LLM, null, inputHash, draft, meter.usage(), started,
					start);
			return draft;
		}
		catch (AgentStepFailedException ex) {
			log.warn("Writer unavailable on attempt {}: reason={}", attempt, ex.getReason());
			trace.record(AgentName.WRITER, attempt, StepSource.SKIPPED, ex.getReason(), inputHash, null,
					meter.usage(), started, start);
			return null;
		}
	}

	private Review review(Trace trace, int attempt, ExtractedPosting posting, String postingText, String cvText,
			MatchResult match, CoverLetterDraft draft) {
		recorder.working(trace.runId(), AgentName.REVIEWER);
		Instant started = clock.instant();
		long start = System.nanoTime();
		StepMeter meter = new StepMeter(trace.userId());
		String inputHash = json.hash(List.of(postingText, cvText, draft.letter()));
		try {
			Review review = reviewer.review(meter, posting, postingText, cvText, match, draft);
			trace.record(AgentName.REVIEWER, attempt, StepSource.LLM, null, inputHash, review, meter.usage(), started,
					start);
			return review;
		}
		catch (AgentStepFailedException ex) {
			log.warn("Reviewer fell back to code checks on attempt {}: reason={}", attempt, ex.getReason());
			Review review = reviewer.checksOnly(match, draft);
			trace.record(AgentName.REVIEWER, attempt, StepSource.FALLBACK, ex.getReason(), inputHash, review,
					meter.usage(), started, start);
			return review;
		}
	}

	private static ExtractedPosting toPosting(AnalysisResponse analysis) {
		return new ExtractedPosting(analysis.company(), analysis.roleTitle(), analysis.seniority(),
				analysis.requiredSkills(), analysis.niceToHaveSkills(), analysis.workMode(), analysis.location(),
				analysis.languages(), analysis.salary(), analysis.summary());
	}

	private record Letter(CoverLetterDraft draft, Review review, int revisions) {
	}

	/** Numbers and stores the steps of one run. */
	private final class Trace {

		private final UUID runId;

		private final UUID userId;

		private int order;

		Trace(UUID runId, UUID userId) {
			this.runId = runId;
			this.userId = userId;
		}

		UUID runId() {
			return runId;
		}

		UUID userId() {
			return userId;
		}

		void record(AgentName agent, int attempt, StepSource source, StepReason reason, String inputHash,
				Object output, StepUsage usage, Instant started, long startNanos) {
			long latencyMs = (System.nanoTime() - startNanos) / 1_000_000;
			recorder.step(runId, ++order, agent, attempt, new StepRecord(source, reason, inputHash,
					output == null ? null : json.write(output), usage, latencyMs, started));
		}

	}

}
