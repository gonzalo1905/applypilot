package com.applypilot.agent;

import static com.applypilot.agent.AgentTestSupport.ANSWERS;
import static com.applypilot.agent.AgentTestSupport.CV;
import static com.applypilot.agent.AgentTestSupport.JSON;
import static com.applypilot.agent.AgentTestSupport.POSTING;
import static com.applypilot.agent.WriterAndReviewerTest.LETTER;
import static com.applypilot.agent.WriterAndReviewerTest.approval;
import static com.applypilot.agent.WriterAndReviewerTest.draftJson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.applypilot.agent.AgentRunExceptions.CvRequiredException;
import com.applypilot.analysis.AiDailyLimitReachedException;
import com.applypilot.analysis.AiUsageLimiter;
import com.applypilot.analysis.AnalysisService;
import com.applypilot.analysis.AnalysisSource;
import com.applypilot.analysis.dto.AnalysisResponse;
import com.applypilot.common.ai.LlmUnavailableException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * The orchestration with real agents and a scripted fake model; only the database side
 * ({@link AgentRunRecorder}) and the Stage 3 analysis are mocked.
 */
class AgentRunOrchestratorTest {

	private static final UUID RUN_ID = UUID.randomUUID();

	private static final UUID USER_ID = UUID.randomUUID();

	private static final UUID APPLICATION_ID = UUID.randomUUID();

	private static final String MATCH_ANSWER = """
			{"skills": [
			  {"skill": "Java", "matched": true, "evidence": "6 years of Java experience"},
			  {"skill": "Spring Boot", "matched": true, "evidence": "REST APIs with Spring Boot"},
			  {"skill": "Kafka", "matched": false, "evidence": null},
			  {"skill": "Docker", "matched": true, "evidence": "Docker on AWS"}
			], "summary": "Good fit without Kafka."}
			""";

	private static final String REJECTION = """
			{"approved": false, "fabricatedClaims": [], "missingRequirements": [],
			 "toneIssues": ["Too generic"], "suggestions": ["Name the payments project"]}
			""";

	private final FakeChatModel fake = new FakeChatModel();

	private final AiUsageLimiter limiter = mock(AiUsageLimiter.class);

	private final AgentRunRecorder recorder = mock(AgentRunRecorder.class);

	private final AnalysisService analysisService = mock(AnalysisService.class);

	private AgentRunOrchestrator orchestrator;

	@BeforeEach
	void setUp() {
		AgentModel model = new AgentModel(fake, limiter, ANSWERS);
		orchestrator = new AgentRunOrchestrator(recorder, analysisService, new MatcherAgent(model, ANSWERS, JSON),
				new KeywordSkillMatcher(), new WriterAgent(model, JSON), new ReviewerAgent(model, JSON), ANSWERS,
				Clock.fixed(Instant.parse("2026-11-22T10:00:00Z"), ZoneOffset.UTC));
		when(recorder.start(RUN_ID)).thenReturn(new AgentRun(APPLICATION_ID, USER_ID, Instant.now()));
		when(recorder.cvText(USER_ID)).thenReturn(CV);
		when(analysisService.get(USER_ID, APPLICATION_ID)).thenReturn(analysis());
	}

	@Test
	void runsTheTeamInOrderAndFinishesWhenTheFirstDraftIsApproved() {
		fake.answers(MATCH_ANSWER, draftJson("en", LETTER), approval(true));

		orchestrator.execute(RUN_ID, null);

		List<Step> steps = steps();
		assertThat(steps).extracting(Step::agent)
			.containsExactly(AgentName.EXTRACTOR, AgentName.MATCHER, AgentName.WRITER, AgentName.REVIEWER);
		assertThat(steps).extracting(Step::source)
			.containsExactly(StepSource.REUSED, StepSource.LLM, StepSource.LLM, StepSource.LLM);
		assertThat(steps.get(1).record().usage().modelCalls()).isEqualTo(1);
		assertThat(steps.get(1).record().output()).contains("\"score\":71");
		assertThat(steps.get(1).record().inputHash()).hasSize(64);
		verify(recorder).matched(eq(RUN_ID), anyString());
		verify(recorder).lettered(eq(RUN_ID), eq(new CoverLetterDraft("en", LETTER.strip())), anyString(), eq(0));
		verify(recorder).finish(RUN_ID, RunStatus.SUCCEEDED, null);
	}

	@Test
	void theReviewerLoopStopsAfterTwoRevisionsEvenIfNeverApproved() {
		fake.answers(MATCH_ANSWER);
		for (int draft = 1; draft <= 3; draft++) {
			fake.answers(draftJson("en", LETTER + " Draft " + draft + "."), REJECTION);
		}

		orchestrator.execute(RUN_ID, null);

		assertThat(fake.remaining()).isZero();
		assertThat(steps()).extracting(step -> step.agent() + "#" + step.attempt())
			.containsExactly("EXTRACTOR#1", "MATCHER#1", "WRITER#1", "REVIEWER#1", "WRITER#2", "REVIEWER#2", "WRITER#3",
					"REVIEWER#3");
		// The last revision is delivered with the review that still asks for changes.
		ArgumentCaptor<String> review = ArgumentCaptor.forClass(String.class);
		verify(recorder).lettered(eq(RUN_ID), eq(new CoverLetterDraft("en", LETTER + " Draft 3.")), review.capture(),
				eq(2));
		assertThat(review.getValue()).contains("\"approved\":false", "Too generic");
		verify(recorder).finish(RUN_ID, RunStatus.SUCCEEDED, null);
	}

	@Test
	void aRevisionGetsTheFeedbackAndAnApprovedRevisionEndsTheLoop() {
		fake.answers(MATCH_ANSWER, draftJson("en", LETTER), REJECTION, draftJson("en", LETTER + " Better."),
				approval(true));

		orchestrator.execute(RUN_ID, null);

		assertThat(fake.structuredRequests.get(2).userPrompt()).contains("<reviewer_feedback>", "Too generic");
		verify(recorder).lettered(eq(RUN_ID), eq(new CoverLetterDraft("en", LETTER + " Better.")), anyString(), eq(1));
	}

	@Test
	void whenTheWriterIsUnavailableTheRunStillDeliversTheMatch() {
		fake.answers(MATCH_ANSWER).fails(() -> new LlmUnavailableException("timeout", null));

		orchestrator.execute(RUN_ID, null);

		List<Step> steps = steps();
		assertThat(steps.get(2).agent()).isEqualTo(AgentName.WRITER);
		assertThat(steps.get(2).source()).isEqualTo(StepSource.SKIPPED);
		assertThat(steps.get(2).record().reason()).isEqualTo(StepReason.PROVIDER_ERROR);
		assertThat(steps.get(3).source()).isEqualTo(StepSource.SKIPPED);
		assertThat(steps.get(3).record().reason()).isEqualTo(StepReason.PREVIOUS_STEP_FAILED);
		verify(recorder).matched(eq(RUN_ID), anyString());
		verify(recorder, never()).lettered(any(), any(), any(), anyInt());
		verify(recorder).finish(RUN_ID, RunStatus.PARTIAL, null);
	}

	@Test
	void aFailedRevisionKeepsTheLastGoodDraft() {
		fake.answers(MATCH_ANSWER, draftJson("en", LETTER), REJECTION)
			.fails(() -> new LlmUnavailableException("timeout", null));

		orchestrator.execute(RUN_ID, null);

		verify(recorder).lettered(eq(RUN_ID), eq(new CoverLetterDraft("en", LETTER.strip())), anyString(), eq(0));
		verify(recorder).finish(RUN_ID, RunStatus.SUCCEEDED, null);
	}

	@Test
	void theMatcherFallsBackToKeywordsAndTheReviewerToCodeChecks() {
		// Tool mode fails, then the Matcher's structured call fails too: keywords take over.
		fake.fails(() -> new LlmUnavailableException("down", null))
			.fails(() -> new LlmUnavailableException("down", null))
			.answers(draftJson("en", LETTER))
			.fails(() -> new LlmUnavailableException("down", null));

		orchestrator.execute(RUN_ID, null);

		List<Step> steps = steps();
		assertThat(steps.get(1).source()).isEqualTo(StepSource.FALLBACK);
		assertThat(steps.get(1).record().output()).contains("Keyword comparison without AI");
		assertThat(steps.get(3).source()).isEqualTo(StepSource.FALLBACK);
		assertThat(steps.get(3).record().output()).contains("\"aiChecked\":false");
		verify(recorder).finish(RUN_ID, RunStatus.SUCCEEDED, null);
	}

	@Test
	void withoutAModelEveryAgentDegradesAndNothingIsCalled() {
		fake.configured = false;

		orchestrator.execute(RUN_ID, null);

		List<Step> steps = steps();
		assertThat(steps.get(1).source()).isEqualTo(StepSource.FALLBACK);
		assertThat(steps.get(1).record().reason()).isEqualTo(StepReason.NOT_CONFIGURED);
		assertThat(steps.get(2).record().reason()).isEqualTo(StepReason.NOT_CONFIGURED);
		assertThat(fake.conversations).isEmpty();
		assertThat(fake.structuredRequests).isEmpty();
		verify(limiter, never()).acquire(any());
		verify(recorder).finish(RUN_ID, RunStatus.PARTIAL, null);
	}

	@Test
	void theDailyLimitAppliesToEveryAgentAndTheRunDegrades() {
		doThrow(new AiDailyLimitReachedException(30)).when(limiter).acquire(USER_ID);

		orchestrator.execute(RUN_ID, null);

		List<Step> steps = steps();
		assertThat(steps.get(1).record().reason()).isEqualTo(StepReason.DAILY_LIMIT_REACHED);
		assertThat(steps.get(2).record().reason()).isEqualTo(StepReason.DAILY_LIMIT_REACHED);
		assertThat(fake.conversations).isEmpty();
		verify(recorder).finish(RUN_ID, RunStatus.PARTIAL, null);
	}

	@Test
	void newPostingTextIsAnalyzedFirstAndCountsAsAModelCall() {
		AnalysisResponse fresh = analysis();
		when(analysisService.analyze(eq(USER_ID), eq(APPLICATION_ID), any())).thenReturn(fresh);
		fake.answers(MATCH_ANSWER, draftJson("en", LETTER), approval(true));

		orchestrator.execute(RUN_ID, "A brand new posting text that is long enough.");

		Step extractor = steps().get(0);
		assertThat(extractor.source()).isEqualTo(StepSource.LLM);
		assertThat(extractor.record().usage().modelCalls()).isEqualTo(1);
		assertThat(extractor.record().usage().promptTokens()).isEqualTo(800);
		verify(analysisService, never()).get(any(), any());
	}

	@Test
	void aRunWithoutACvFailsWithAClearCode() {
		when(recorder.cvText(USER_ID)).thenThrow(new CvRequiredException());

		orchestrator.execute(RUN_ID, null);

		verify(recorder).finish(RUN_ID, RunStatus.FAILED, "CV_REQUIRED");
		assertThat(fake.conversations).isEmpty();
	}

	@Test
	void aRunThatWasAlreadyStartedOrDeletedIsNotRunTwice() {
		when(recorder.start(RUN_ID)).thenReturn(null);

		orchestrator.execute(RUN_ID, null);

		verify(recorder, never()).finish(any(), any(), any());
		verify(analysisService, never()).get(any(), any());
	}

	@Test
	void aFailureWhileRecordingTheEndStillTriesToMarkTheRunFailed() {
		fake.answers(MATCH_ANSWER, draftJson("en", LETTER), approval(true));
		doThrow(new IllegalStateException("database down")).when(recorder).finish(RUN_ID, RunStatus.SUCCEEDED, null);

		orchestrator.execute(RUN_ID, null);

		verify(recorder).finish(RUN_ID, RunStatus.FAILED, "INTERNAL_ERROR");
	}

	@Test
	void anUnexpectedErrorFailsTheRunInsteadOfLeavingItRunning() {
		when(analysisService.get(USER_ID, APPLICATION_ID)).thenThrow(new IllegalStateException("bug"));

		orchestrator.execute(RUN_ID, null);

		verify(recorder).finish(RUN_ID, RunStatus.FAILED, "INTERNAL_ERROR");
	}

	private List<Step> steps() {
		ArgumentCaptor<Integer> order = ArgumentCaptor.forClass(Integer.class);
		ArgumentCaptor<AgentName> agent = ArgumentCaptor.forClass(AgentName.class);
		ArgumentCaptor<Integer> attempt = ArgumentCaptor.forClass(Integer.class);
		ArgumentCaptor<StepRecord> record = ArgumentCaptor.forClass(StepRecord.class);
		verify(recorder, org.mockito.Mockito.atLeastOnce()).step(eq(RUN_ID), order.capture(), agent.capture(),
				attempt.capture(), record.capture());
		assertThat(order.getAllValues()).isSorted().doesNotHaveDuplicates();
		List<Step> steps = new java.util.ArrayList<>();
		for (int i = 0; i < order.getAllValues().size(); i++) {
			steps.add(new Step(agent.getAllValues().get(i), attempt.getAllValues().get(i),
					record.getAllValues().get(i).source(), record.getAllValues().get(i)));
		}
		return steps;
	}

	private static AnalysisResponse analysis() {
		return new AnalysisResponse(UUID.randomUUID(), APPLICATION_ID,
				"Acme is hiring a Senior Backend Engineer: Java, Spring Boot, Kafka. Nice to have: Docker.",
				POSTING.company(), POSTING.roleTitle(), POSTING.seniority(), POSTING.requiredSkills(),
				POSTING.niceToHaveSkills(), POSTING.workMode(), POSTING.location(), POSTING.languages(), null,
				POSTING.summary(), AnalysisSource.LLM, null, FakeChatModel.MODEL, false, Instant.now(), 1, 800, 200);
	}

	private record Step(AgentName agent, int attempt, StepSource source, StepRecord record) {
	}

}
