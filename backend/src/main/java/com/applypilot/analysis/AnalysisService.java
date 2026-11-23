package com.applypilot.analysis;

import com.applypilot.analysis.JobPostingExtractor.Extraction;
import com.applypilot.analysis.dto.AnalysisResponse;
import com.applypilot.analysis.dto.AnalyzeRequest;
import com.applypilot.application.ApplicationNotFoundException;
import com.applypilot.application.JobApplicationRepository;
import com.applypilot.common.ai.ChatCompletionClient;
import com.applypilot.common.error.InvalidRequestException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Analyzes the job posting of one application: turns the pasted text into structured data
 * (skills, seniority, work mode...) and stores it with the application.
 *
 * <p>Order of preference, cheapest first:
 * <ol>
 * <li><b>Cache.</b> If this user already got a model result for exactly the same text, it is
 * reused: no model call and no quota used. Only model results are reused, so a posting that
 * once fell back to keywords gets a real analysis as soon as the model is back.</li>
 * <li><b>Model.</b> Costs one of the user's daily AI calls; when none are left the request is
 * answered with 429 instead of quietly giving a worse result.</li>
 * <li><b>Keyword fallback.</b> When no API key is configured or the model fails (rate limit,
 * timeout, invalid output twice). The result is marked {@code FALLBACK} with the reason, so
 * the user can tell it apart from a model result.</li>
 * </ol>
 *
 * <p>The model call can take seconds, so it runs outside any database transaction; only the
 * short reads and the final write are transactional ({@link TransactionTemplate}).
 */
@Service
public class AnalysisService {

	private static final Pattern LINE_BREAKS = Pattern.compile("\\r\\n?");

	private static final Pattern TRAILING_SPACES = Pattern.compile("[ \\t]+\\n");

	private static final Pattern BLANK_LINES = Pattern.compile("\\n{3,}");

	private static final Logger log = LoggerFactory.getLogger(AnalysisService.class);

	private final JobApplicationRepository applications;

	private final JobAnalysisRepository analyses;

	private final JobPostingExtractor llmExtractor;

	private final JobPostingExtractor keywordExtractor;

	private final ChatCompletionClient chatClient;

	private final AiUsageLimiter usageLimiter;

	private final Clock clock;

	private final TransactionTemplate transaction;

	public AnalysisService(JobApplicationRepository applications, JobAnalysisRepository analyses,
			LlmJobPostingExtractor llmExtractor, KeywordJobPostingExtractor keywordExtractor,
			ChatCompletionClient chatClient, AiUsageLimiter usageLimiter, Clock clock, TransactionTemplate transaction) {
		this.applications = applications;
		this.analyses = analyses;
		this.llmExtractor = llmExtractor;
		this.keywordExtractor = keywordExtractor;
		this.chatClient = chatClient;
		this.usageLimiter = usageLimiter;
		this.clock = clock;
		this.transaction = transaction;
	}

	public AnalysisResponse get(UUID userId, UUID applicationId) {
		return transaction.execute(status -> {
			requireOwnedApplication(userId, applicationId);
			return analyses.findByApplicationIdAndUserId(applicationId, userId)
				.map(analysis -> AnalysisResponse.from(analysis, false))
				.orElseThrow(AnalysisNotFoundException::new);
		});
	}

	public AnalysisResponse analyze(UUID userId, UUID applicationId, AnalyzeRequest request) {
		String postingText = normalize(request.postingText());
		if (postingText.length() < AnalyzeRequest.MIN_LENGTH) {
			throw new InvalidRequestException("The job posting is too short to analyze");
		}
		String postingHash = sha256(postingText);

		Optional<JobAnalysis> cached = transaction.execute(status -> {
			requireOwnedApplication(userId, applicationId);
			return analyses.findFirstByUserIdAndPostingHashAndSourceOrderByAnalyzedAtDesc(userId, postingHash,
					AnalysisSource.LLM);
		});
		if (cached.isPresent()) {
			JobAnalysis hit = cached.get();
			Result reused = new Result(hit.toExtractedPosting(), AnalysisSource.LLM, null, hit.getModel(), 0, null,
					null);
			return store(userId, applicationId, postingText, postingHash, reused, true);
		}

		return store(userId, applicationId, postingText, postingHash, extract(userId, postingText), false);
	}

	/** Runs the model if possible, the keyword extractor otherwise. */
	private Result extract(UUID userId, String postingText) {
		if (!chatClient.isConfigured()) {
			return fallback(postingText, FallbackReason.NOT_CONFIGURED);
		}
		// Throws 429 when the user has no calls left today; nothing is stored in that case.
		usageLimiter.acquire(userId);
		try {
			Extraction extraction = llmExtractor.extract(postingText);
			return new Result(extraction.posting(), AnalysisSource.LLM, null, extraction.model(),
					extraction.modelCalls(), extraction.promptTokens(), extraction.completionTokens());
		}
		catch (ExtractionFailedException ex) {
			log.warn("Job posting extraction fell back to keywords: reason={}", ex.getReason());
			return fallback(postingText, ex.getReason());
		}
	}

	private Result fallback(String postingText, FallbackReason reason) {
		Extraction extraction = keywordExtractor.extract(postingText);
		return new Result(extraction.posting(), AnalysisSource.FALLBACK, reason, null, 0, null, null);
	}

	/**
	 * Creates or replaces the application's analysis. The board lock serializes this with
	 * other writes of the same user, so two simultaneous first analyses of one application
	 * cannot both insert a row. The application is checked again because it may have been
	 * deleted while the model was working.
	 */
	private AnalysisResponse store(UUID userId, UUID applicationId, String postingText, String postingHash,
			Result result, boolean cached) {
		return transaction.execute(status -> {
			applications.lockBoard(userId);
			requireOwnedApplication(userId, applicationId);
			JobAnalysis analysis = analyses.findByApplicationIdAndUserId(applicationId, userId)
				.orElseGet(() -> new JobAnalysis(applicationId, userId));
			analysis.replace(postingText, postingHash, result.posting(), result.source(), result.fallbackReason(),
					result.model(), clock.instant());
			analysis.recordUsage(result.modelCalls(), result.promptTokens(), result.completionTokens());
			return AnalysisResponse.from(analyses.save(analysis), cached);
		});
	}

	private void requireOwnedApplication(UUID userId, UUID applicationId) {
		if (applications.findByIdAndUserId(applicationId, userId).isEmpty()) {
			throw new ApplicationNotFoundException();
		}
	}

	/**
	 * Same posting, same text: unifies line breaks, drops trailing spaces and extra blank
	 * lines, and removes NUL (PostgreSQL cannot store it). Copying a posting twice from the
	 * same page then gives the same hash, so the cache recognizes it.
	 */
	static String normalize(String text) {
		String unified = LINE_BREAKS.matcher(text.replace("\u0000", "")).replaceAll("\n");
		String trimmedLines = TRAILING_SPACES.matcher(unified).replaceAll("\n");
		return BLANK_LINES.matcher(trimmedLines).replaceAll("\n\n").strip();
	}

	static String sha256(String text) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 is always available in the JDK", ex);
		}
	}

	/** @param modelCalls what producing this result cost now: 0 for a reuse or a fallback */
	private record Result(ExtractedPosting posting, AnalysisSource source, FallbackReason fallbackReason,
			String model, int modelCalls, Integer promptTokens, Integer completionTokens) {
	}

}
