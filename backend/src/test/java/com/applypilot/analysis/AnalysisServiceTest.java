package com.applypilot.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.applypilot.analysis.JobPostingExtractor.Extraction;
import com.applypilot.analysis.dto.AnalysisResponse;
import com.applypilot.analysis.dto.AnalyzeRequest;
import com.applypilot.application.ApplicationNotFoundException;
import com.applypilot.application.ApplicationStatus;
import com.applypilot.application.JobApplication;
import com.applypilot.application.JobApplicationRepository;
import com.applypilot.application.WorkMode;
import com.applypilot.common.ai.ChatCompletionClient;
import com.applypilot.common.error.InvalidRequestException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The mocked analysis repository keeps saved rows in {@link #stored}, the way the database
 * would, so tests can check what ended up stored.
 */
@ExtendWith(MockitoExtension.class)
class AnalysisServiceTest {

	private static final Instant NOW = Instant.parse("2026-11-20T10:00:00Z");

	private static final UUID USER_ID = UUID.randomUUID();

	private static final UUID APPLICATION_ID = UUID.randomUUID();

	private static final String POSTING = "Senior Java developer at Acme. Spring Boot and PostgreSQL required.";

	private static final ExtractedPosting LLM_POSTING = new ExtractedPosting("Acme", "Java Developer",
			Seniority.SENIOR, List.of("Java", "Spring Boot", "PostgreSQL"), List.of(), WorkMode.REMOTE, null,
			List.of("English"), null, "A backend role.");

	private static final ExtractedPosting KEYWORD_POSTING = new ExtractedPosting(null, null, Seniority.SENIOR,
			List.of("Java"), List.of(), null, null, List.of(), null, "Keyword-based extraction...");

	@Mock
	private JobApplicationRepository applications;

	@Mock
	private JobAnalysisRepository analyses;

	@Mock
	private LlmJobPostingExtractor llmExtractor;

	@Mock
	private KeywordJobPostingExtractor keywordExtractor;

	@Mock
	private ChatCompletionClient chatClient;

	@Mock
	private AiUsageLimiter usageLimiter;

	private AnalysisService service;

	private final List<JobAnalysis> stored = new ArrayList<>();

	@BeforeEach
	void setUp() {
		service = new AnalysisService(applications, analyses, llmExtractor, keywordExtractor, chatClient, usageLimiter,
				Clock.fixed(NOW, ZoneOffset.UTC), directTransactions());

		JobApplication application = new JobApplication(USER_ID, ApplicationStatus.SAVED, 0);
		ReflectionTestUtils.setField(application, "id", APPLICATION_ID);
		lenient().when(applications.findByIdAndUserId(APPLICATION_ID, USER_ID)).thenReturn(Optional.of(application));
		lenient().when(analyses.findByApplicationIdAndUserId(any(), any()))
			.thenAnswer(invocation -> stored.stream()
				.filter(row -> row.getApplicationId().equals(invocation.getArgument(0))
						&& row.getUserId().equals(invocation.getArgument(1)))
				.findFirst());
		lenient().when(analyses.save(any())).thenAnswer(invocation -> {
			JobAnalysis row = invocation.getArgument(0);
			if (!stored.contains(row)) {
				ReflectionTestUtils.setField(row, "id", UUID.randomUUID());
				stored.add(row);
			}
			return row;
		});
		lenient().when(keywordExtractor.extract(anyString())).thenReturn(new Extraction(KEYWORD_POSTING, null));
	}

	@Test
	void usesTheModelAndCountsOneAiCall() {
		when(chatClient.isConfigured()).thenReturn(true);
		when(llmExtractor.extract(POSTING)).thenReturn(new Extraction(LLM_POSTING, "openai/gpt-oss-120b"));

		AnalysisResponse response = service.analyze(USER_ID, APPLICATION_ID, new AnalyzeRequest(POSTING));

		assertThat(response.source()).isEqualTo(AnalysisSource.LLM);
		assertThat(response.fallbackReason()).isNull();
		assertThat(response.model()).isEqualTo("openai/gpt-oss-120b");
		assertThat(response.cached()).isFalse();
		assertThat(response.requiredSkills()).containsExactly("Java", "Spring Boot", "PostgreSQL");
		assertThat(response.analyzedAt()).isEqualTo(NOW);
		verify(usageLimiter).acquire(USER_ID);
		verify(applications).lockBoard(USER_ID);
		assertThat(stored).singleElement().satisfies(row -> {
			assertThat(row.getPostingText()).isEqualTo(POSTING);
			assertThat(row.getPostingHash()).isEqualTo(AnalysisService.sha256(POSTING)).hasSize(64);
		});
	}

	@Test
	void fallsBackToKeywordsWithTheReasonWhenTheModelFails() {
		when(chatClient.isConfigured()).thenReturn(true);
		when(llmExtractor.extract(POSTING))
			.thenThrow(new ExtractionFailedException(FallbackReason.INVALID_OUTPUT, "bad twice", null));

		AnalysisResponse response = service.analyze(USER_ID, APPLICATION_ID, new AnalyzeRequest(POSTING));

		assertThat(response.source()).isEqualTo(AnalysisSource.FALLBACK);
		assertThat(response.fallbackReason()).isEqualTo(FallbackReason.INVALID_OUTPUT);
		assertThat(response.model()).isNull();
		assertThat(response.requiredSkills()).containsExactly("Java");
	}

	@Test
	void fallsBackWithoutUsingQuotaWhenNoApiKeyIsConfigured() {
		when(chatClient.isConfigured()).thenReturn(false);

		AnalysisResponse response = service.analyze(USER_ID, APPLICATION_ID, new AnalyzeRequest(POSTING));

		assertThat(response.source()).isEqualTo(AnalysisSource.FALLBACK);
		assertThat(response.fallbackReason()).isEqualTo(FallbackReason.NOT_CONFIGURED);
		verifyNoInteractions(usageLimiter, llmExtractor);
	}

	@Test
	void reusesAnEarlierModelResultForTheSameTextWithoutCallingTheModel() {
		JobAnalysis earlier = new JobAnalysis(UUID.randomUUID(), USER_ID);
		earlier.replace(POSTING, AnalysisService.sha256(POSTING), LLM_POSTING, AnalysisSource.LLM, null,
				"openai/gpt-oss-120b", NOW.minusSeconds(3600));
		when(analyses.findFirstByUserIdAndPostingHashAndSourceOrderByAnalyzedAtDesc(USER_ID,
				AnalysisService.sha256(POSTING), AnalysisSource.LLM))
			.thenReturn(Optional.of(earlier));

		// Pasted again from another page: surrounding blank lines and spaces do not matter.
		AnalysisResponse response = service.analyze(USER_ID, APPLICATION_ID,
				new AnalyzeRequest("\r\n  " + POSTING + "   \r\n\r\n\r\n"));

		assertThat(response.cached()).isTrue();
		assertThat(response.source()).isEqualTo(AnalysisSource.LLM);
		assertThat(response.company()).isEqualTo("Acme");
		assertThat(response.applicationId()).isEqualTo(APPLICATION_ID);
		verifyNoInteractions(usageLimiter, llmExtractor, chatClient);
	}

	@Test
	void answersTooManyRequestsWhenTheDailyLimitIsReachedAndStoresNothing() {
		when(chatClient.isConfigured()).thenReturn(true);
		doThrow(new AiDailyLimitReachedException(20)).when(usageLimiter).acquire(USER_ID);

		assertThatThrownBy(() -> service.analyze(USER_ID, APPLICATION_ID, new AnalyzeRequest(POSTING)))
			.isInstanceOf(AiDailyLimitReachedException.class)
			.extracting("status")
			.hasToString("429 TOO_MANY_REQUESTS");
		verifyNoInteractions(llmExtractor, keywordExtractor);
		verify(analyses, never()).save(any());
	}

	@Test
	void anotherUsersApplicationIsNotFoundAndCostsNothing() {
		UUID otherUser = UUID.randomUUID();
		when(applications.findByIdAndUserId(APPLICATION_ID, otherUser)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.analyze(otherUser, APPLICATION_ID, new AnalyzeRequest(POSTING)))
			.isInstanceOf(ApplicationNotFoundException.class);
		assertThatThrownBy(() -> service.get(otherUser, APPLICATION_ID))
			.isInstanceOf(ApplicationNotFoundException.class);
		verifyNoInteractions(usageLimiter, llmExtractor, keywordExtractor, chatClient);
	}

	@Test
	void reanalyzingReplacesTheExistingAnalysis() {
		when(chatClient.isConfigured()).thenReturn(false);
		service.analyze(USER_ID, APPLICATION_ID, new AnalyzeRequest(POSTING));

		when(chatClient.isConfigured()).thenReturn(true);
		when(llmExtractor.extract(anyString())).thenReturn(new Extraction(LLM_POSTING, "openai/gpt-oss-120b"));
		AnalysisResponse second = service.analyze(USER_ID, APPLICATION_ID,
				new AnalyzeRequest(POSTING + " Kubernetes is a plus."));

		assertThat(stored).hasSize(1);
		assertThat(second.source()).isEqualTo(AnalysisSource.LLM);
		assertThat(stored.getFirst().getPostingText()).endsWith("Kubernetes is a plus.");
	}

	@Test
	void rejectsTextThatIsTooShortOnceNormalized() {
		String padded = "short" + "\u0000".repeat(30) + "   \n\n\n\n";

		assertThatThrownBy(() -> service.analyze(USER_ID, APPLICATION_ID, new AnalyzeRequest(padded)))
			.isInstanceOf(InvalidRequestException.class);
		verifyNoInteractions(analyses, usageLimiter);
	}

	@Test
	void getReturnsTheStoredAnalysisOrNotFound() {
		assertThatThrownBy(() -> service.get(USER_ID, APPLICATION_ID)).isInstanceOf(AnalysisNotFoundException.class);

		when(chatClient.isConfigured()).thenReturn(false);
		service.analyze(USER_ID, APPLICATION_ID, new AnalyzeRequest(POSTING));

		AnalysisResponse response = service.get(USER_ID, APPLICATION_ID);
		assertThat(response.source()).isEqualTo(AnalysisSource.FALLBACK);
		assertThat(response.cached()).isFalse();
		assertThat(response.postingText()).isEqualTo(POSTING);
	}

	@Test
	void normalizationMakesTheSameCopiedPostingHashTheSame() {
		String windows = "Title\r\n\r\n\r\n\r\nJava  \r\nSpring\u0000 Boot\r\n";
		String unix = "Title\n\nJava\nSpring Boot";

		assertThat(AnalysisService.normalize(windows)).isEqualTo(unix);
		assertThat(AnalysisService.sha256(AnalysisService.normalize(windows))).isEqualTo(AnalysisService.sha256(unix));
	}

	/** Runs the callback directly: unit tests have no database, so there is nothing to commit. */
	private static TransactionTemplate directTransactions() {
		return new TransactionTemplate() {
			@Override
			public <T> T execute(TransactionCallback<T> action) {
				return action.doInTransaction(new SimpleTransactionStatus());
			}
		};
	}

}
