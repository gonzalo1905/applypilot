package com.applypilot.agent;

import static com.applypilot.agent.AgentTestSupport.ANSWERS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.applypilot.agent.AgentRunExceptions.AgentRunsBusyException;
import com.applypilot.agent.AgentRunExceptions.AnalysisRequiredException;
import com.applypilot.agent.AgentRunExceptions.CvRequiredException;
import com.applypilot.agent.dto.AgentRunResponse;
import com.applypilot.agent.dto.StartAgentRunRequest;
import com.applypilot.analysis.AiDailyLimitReachedException;
import com.applypilot.analysis.AiUsageLimiter;
import com.applypilot.analysis.JobAnalysis;
import com.applypilot.analysis.JobAnalysisRepository;
import com.applypilot.application.ApplicationNotFoundException;
import com.applypilot.application.ApplicationStatus;
import com.applypilot.application.JobApplication;
import com.applypilot.application.JobApplicationRepository;
import com.applypilot.common.ai.AiProperties;
import com.applypilot.cv.Cv;
import com.applypilot.cv.CvRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The mocked run repository keeps saved runs in {@link #stored}, the way the database would,
 * so tests can check what ended up stored and read it back.
 */
@ExtendWith(MockitoExtension.class)
class AgentRunServiceTest {

	private static final Instant NOW = Instant.parse("2026-11-22T10:00:00Z");

	private static final UUID USER_ID = UUID.randomUUID();

	private static final UUID OTHER_USER_ID = UUID.randomUUID();

	private static final UUID APPLICATION_ID = UUID.randomUUID();

	private static final String POSTING_TEXT = "Senior Java developer at Acme. Spring Boot and PostgreSQL required.";

	@Mock
	private AgentRunRepository runs;

	@Mock
	private AgentRunStepRepository steps;

	@Mock
	private JobApplicationRepository applications;

	@Mock
	private JobAnalysisRepository analyses;

	@Mock
	private CvRepository cvs;

	@Mock
	private AgentModel model;

	@Mock
	private AiUsageLimiter usageLimiter;

	@Mock
	private AgentRunOrchestrator orchestrator;

	@Mock
	private AgentRunExecutor executor;

	@Mock
	private AgentRunRecorder recorder;

	private AgentRunService service;

	private final List<AgentRun> stored = new ArrayList<>();

	@BeforeEach
	void setUp() {
		service = new AgentRunService(runs, steps, applications, analyses, cvs, model, usageLimiter,
				new AiProperties("test-model", 0, 1000, null, 20), orchestrator, executor, recorder, ANSWERS,
				Clock.fixed(NOW, ZoneOffset.UTC), directTransactions());

		JobApplication application = new JobApplication(USER_ID, ApplicationStatus.SAVED, 0);
		ReflectionTestUtils.setField(application, "id", APPLICATION_ID);
		lenient().when(applications.findByIdAndUserId(APPLICATION_ID, USER_ID)).thenReturn(Optional.of(application));
		lenient().when(cvs.findByUserId(USER_ID)).thenReturn(Optional.of(new Cv(USER_ID)));
		lenient().when(analyses.findByApplicationIdAndUserId(APPLICATION_ID, USER_ID))
			.thenReturn(Optional.of(new JobAnalysis(APPLICATION_ID, USER_ID)));
		lenient().when(runs.save(any())).thenAnswer(invocation -> {
			AgentRun run = invocation.getArgument(0);
			ReflectionTestUtils.setField(run, "id", UUID.randomUUID());
			stored.add(run);
			return run;
		});
		lenient().when(runs.findById(any()))
			.thenAnswer(invocation -> stored.stream()
				.filter(run -> run.getId().equals(invocation.getArgument(0)))
				.findFirst());
	}

	@Test
	void queuesANewPendingRunAndHandsTheTextToTheOrchestrator() {
		when(model.isConfigured()).thenReturn(true);
		when(usageLimiter.remaining(USER_ID)).thenReturn(10);

		AgentRunResponse response = service.start(USER_ID, APPLICATION_ID, new StartAgentRunRequest(POSTING_TEXT));

		assertThat(stored).singleElement().satisfies(run -> {
			assertThat(run.getUserId()).isEqualTo(USER_ID);
			assertThat(run.getApplicationId()).isEqualTo(APPLICATION_ID);
			assertThat(run.getStatus()).isEqualTo(RunStatus.PENDING);
			assertThat(run.getCreatedAt()).isEqualTo(NOW);
		});
		assertThat(response.id()).isEqualTo(stored.get(0).getId());
		assertThat(response.status()).isEqualTo(RunStatus.PENDING);
		verify(applications).lockBoard(USER_ID);

		ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
		verify(executor).submit(task.capture());
		task.getValue().run();
		verify(orchestrator).execute(stored.get(0).getId(), POSTING_TEXT);
	}

	@Test
	void returnsTheActiveRunInsteadOfStartingASecondOne() {
		AgentRun active = new AgentRun(APPLICATION_ID, USER_ID, NOW.minusSeconds(5));
		ReflectionTestUtils.setField(active, "id", UUID.randomUUID());
		stored.add(active);
		when(runs.findFirstByApplicationIdAndUserIdAndStatusIn(any(), any(), any())).thenReturn(Optional.of(active));

		AgentRunResponse response = service.start(USER_ID, APPLICATION_ID, null);

		assertThat(response.id()).isEqualTo(active.getId());
		assertThat(stored).containsExactly(active);
		verify(runs, never()).save(any());
		verifyNoInteractions(executor);
	}

	@Test
	void anActiveRunOlderThanAnyRealRunIsFailedAndNoLongerBlocksANewOne() {
		AgentRun stale = new AgentRun(APPLICATION_ID, USER_ID, NOW.minus(AgentRunService.STALE_AFTER).minusSeconds(1));
		ReflectionTestUtils.setField(stale, "id", UUID.randomUUID());
		stale.start(NOW.minus(AgentRunService.STALE_AFTER));
		when(runs.findFirstByApplicationIdAndUserIdAndStatusIn(any(), any(), any())).thenReturn(Optional.of(stale));

		AgentRunResponse response = service.start(USER_ID, APPLICATION_ID, null);

		assertThat(stale.getStatus()).isEqualTo(RunStatus.FAILED);
		assertThat(stale.getErrorCode()).isEqualTo("TIMED_OUT");
		assertThat(response.id()).isNotEqualTo(stale.getId());
		verify(executor).submit(any());
	}

	@Test
	void refusesWithCvRequiredWhenTheUserHasNoCv() {
		when(cvs.findByUserId(USER_ID)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.start(USER_ID, APPLICATION_ID, new StartAgentRunRequest(POSTING_TEXT)))
			.isInstanceOf(CvRequiredException.class);
		assertThat(stored).isEmpty();
		verifyNoInteractions(executor);
	}

	@Test
	void refusesWithAnalysisRequiredWhenThereIsNoStoredAnalysisAndNoText() {
		when(analyses.findByApplicationIdAndUserId(APPLICATION_ID, USER_ID)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.start(USER_ID, APPLICATION_ID, new StartAgentRunRequest("   ")))
			.isInstanceOf(AnalysisRequiredException.class);
		assertThat(stored).isEmpty();
		verifyNoInteractions(executor);
	}

	@Test
	void startsWithoutAStoredAnalysisWhenThePostingTextIsGiven() {
		lenient().when(analyses.findByApplicationIdAndUserId(APPLICATION_ID, USER_ID)).thenReturn(Optional.empty());

		AgentRunResponse response = service.start(USER_ID, APPLICATION_ID, new StartAgentRunRequest(POSTING_TEXT));

		assertThat(stored).hasSize(1);
		assertThat(response.status()).isEqualTo(RunStatus.PENDING);
		verify(executor).submit(any());
	}

	@Test
	void refusesWith429WhenTheModelIsConfiguredAndNoCallIsLeftToday() {
		when(model.isConfigured()).thenReturn(true);
		when(usageLimiter.remaining(USER_ID)).thenReturn(0);

		assertThatThrownBy(() -> service.start(USER_ID, APPLICATION_ID, null))
			.isInstanceOf(AiDailyLimitReachedException.class)
			.hasMessageContaining("20");
		assertThat(stored).isEmpty();
		verifyNoInteractions(executor);
	}

	@Test
	void refusesARunThatCouldOnlyDegradeForLackOfCalls() {
		when(model.isConfigured()).thenReturn(true);
		when(usageLimiter.remaining(USER_ID)).thenReturn(AgentRunService.MIN_CALLS_TO_START - 1);

		// New posting text means an analysis call too: 4 are needed.
		assertThatThrownBy(() -> service.start(USER_ID, APPLICATION_ID, new StartAgentRunRequest(POSTING_TEXT)))
			.isInstanceOf(AgentRunExceptions.NotEnoughAiCallsException.class)
			.hasMessageContaining("needs at least 4 AI calls and you have 3 left");
		assertThat(stored).isEmpty();
	}

	@Test
	void startsWithExactlyTheMinimumNumberOfCalls() {
		when(model.isConfigured()).thenReturn(true);
		when(usageLimiter.remaining(USER_ID)).thenReturn(AgentRunService.MIN_CALLS_TO_START);

		service.start(USER_ID, APPLICATION_ID, new StartAgentRunRequest(POSTING_TEXT));

		assertThat(stored).hasSize(1);
	}

	@Test
	void reusingTheStoredAnalysisNeedsOneCallFewer() {
		when(model.isConfigured()).thenReturn(true);
		when(usageLimiter.remaining(USER_ID)).thenReturn(3);

		service.start(USER_ID, APPLICATION_ID, null);
		assertThat(stored).hasSize(1);

		stored.clear();
		when(usageLimiter.remaining(USER_ID)).thenReturn(2);
		assertThatThrownBy(() -> service.start(USER_ID, APPLICATION_ID, null))
			.hasMessageContaining("needs at least 3 AI calls and you have 2 left");
	}

	@Test
	void startsWithoutQuotaWhenNoModelIsConfigured() {
		when(model.isConfigured()).thenReturn(false);
		lenient().when(usageLimiter.remaining(USER_ID)).thenReturn(0);

		AgentRunResponse response = service.start(USER_ID, APPLICATION_ID, null);

		assertThat(stored).hasSize(1);
		assertThat(response.status()).isEqualTo(RunStatus.PENDING);
		verify(executor).submit(any());
	}

	@Test
	void marksTheRunFailedAndAnswersBusyWhenTheQueueIsFull() {
		doThrow(new RejectedExecutionException("full")).when(executor).submit(any());

		assertThatThrownBy(() -> service.start(USER_ID, APPLICATION_ID, null))
			.isInstanceOf(AgentRunsBusyException.class);
		assertThat(stored).hasSize(1);
		verify(recorder).finish(stored.get(0).getId(), RunStatus.FAILED, "AGENT_RUNS_BUSY");
	}

	@Test
	void anotherUsersApplicationIsNotFoundAndNothingIsSaved() {
		when(applications.findByIdAndUserId(APPLICATION_ID, OTHER_USER_ID)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.start(OTHER_USER_ID, APPLICATION_ID, new StartAgentRunRequest(POSTING_TEXT)))
			.isInstanceOf(ApplicationNotFoundException.class);
		verify(runs, never()).save(any());
		verifyNoInteractions(executor);
	}

	private static TransactionTemplate directTransactions() {
		return new TransactionTemplate() {
			@Override
			public <T> T execute(TransactionCallback<T> action) {
				return action.doInTransaction(new SimpleTransactionStatus());
			}
		};
	}

}
