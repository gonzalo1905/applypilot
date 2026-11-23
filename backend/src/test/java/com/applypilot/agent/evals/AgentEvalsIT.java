package com.applypilot.agent.evals;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.mock;

import com.applypilot.TestcontainersConfiguration;
import com.applypilot.agent.AgentModel;
import com.applypilot.agent.AgentStepFailedException;
import com.applypilot.agent.CoverLetterDraft;
import com.applypilot.agent.JsonAnswers;
import com.applypilot.agent.MatchResult;
import com.applypilot.agent.MatcherAgent;
import com.applypilot.agent.PostingLanguage;
import com.applypilot.agent.Review;
import com.applypilot.agent.ReviewerAgent;
import com.applypilot.agent.StepMeter;
import com.applypilot.agent.WriterAgent;
import com.applypilot.agent.evals.EvalDataset.EvalCase;
import com.applypilot.agent.evals.EvalReport.CaseResult;
import com.applypilot.agent.evals.EvalReport.Step;
import com.applypilot.analysis.AiUsageLimiter;
import com.applypilot.analysis.ExtractedPosting;
import com.applypilot.analysis.ExtractionFailedException;
import com.applypilot.analysis.JobPostingExtractor.Extraction;
import com.applypilot.analysis.LlmJobPostingExtractor;
import com.applypilot.analysis.Seniority;
import com.applypilot.common.ai.AiProperties;
import com.applypilot.common.ai.ChatCompletionClient;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.json.JsonMapper;

/**
 * Offline evals of the agents against the golden dataset, with the real model. Opt-in, it
 * spends quota:
 *
 * <pre>
 * ./mvnw verify -Devals=true -Dit.test=AgentEvalsIT -Dtest=none -Dsurefire.failIfNoSpecifiedTests=false
 * </pre>
 *
 * Options: {@code -Devals.cases=N} runs the first N cases, {@code -Devals.only=id1,id2} picks
 * cases, {@code -Devals.tpm=6000} sets the tokens per minute to stay under, and
 * {@code -Devals.out=dir} where the report goes (default {@code target/evals}).
 *
 * <p>Each agent is measured on its own: the Matcher gets the gold skills, not the extracted
 * ones, so an extraction miss does not show up as a matching miss. The Writer gets the real
 * extraction and match, as in the app. The run fails only on the security gate (an injection
 * canary in any output); everything else is reported, to be compared with the baseline.
 */
@SpringBootTest(properties = "spring.ai.openai.api-key=${AI_API_KEY:${GROQ_API_KEY:not-configured}}")
@Import(TestcontainersConfiguration.class)
@EnabledIfSystemProperty(named = "evals", matches = "true")
class AgentEvalsIT {

	/** Same cap as the orchestrator's evaluator-optimizer loop. */
	private static final int MAX_REVISIONS = Integer.getInteger("evals.revisions", 2);

	@Autowired
	private ChatCompletionClient realClient;

	@Autowired
	private JsonMapper jsonMapper;

	@Autowired
	private AiProperties aiProperties;

	@Test
	void runTheAgentEvals() throws IOException {
		assumeTrue(realClient.isConfigured(), "Set GROQ_API_KEY (or AI_API_KEY) to run the evals");
		EvalDataset dataset = jsonMapper.readValue(
				new ClassPathResource("evals/cases.json").getContentAsString(StandardCharsets.UTF_8), EvalDataset.class);
		List<EvalCase> cases = select(dataset.cases());
		assertThat(cases).as("cases selected by evals.cases / evals.only").isNotEmpty();

		PacedChatClient client = new PacedChatClient(realClient, Integer.getInteger("evals.tpm", 6000));
		// The evals are a developer tool: the app's per-user daily limit does not apply.
		AgentModel model = new AgentModel(client, mock(AiUsageLimiter.class), new JsonAnswers(jsonMapper));
		Agents agents = new Agents(new LlmJobPostingExtractor(client, jsonMapper),
				new MatcherAgent(model, new JsonAnswers(jsonMapper), jsonMapper), new WriterAgent(model, jsonMapper),
				new ReviewerAgent(model, jsonMapper), new EvalJudge(model));

		Instant started = Instant.now();
		List<CaseResult> results = new ArrayList<>();
		for (EvalCase evalCase : cases) {
			System.out.println("EVAL running " + evalCase.id());
			results.add(runCase(evalCase, agents, client));
		}
		EvalReport report = EvalReport.of(dataset.version(), aiProperties.model(), started, Instant.now(), results,
				client.rateLimitRetries(), client.waitedMs());

		Path out = Path.of(System.getProperty("evals.out", "target/evals"));
		Files.createDirectories(out);
		Files.writeString(out.resolve("report.json"), jsonMapper.writerWithDefaultPrettyPrinter()
			.writeValueAsString(report), StandardCharsets.UTF_8);
		Files.writeString(out.resolve("report.md"), report.toMarkdown(), StandardCharsets.UTF_8);
		System.out.println("EVAL report written to " + out.toAbsolutePath());
		System.out.println(report.summaryLine());

		// The security gate: injected text must never reach an output.
		assertThat(report.summary().casesWithLeaks()).as("cases where injected text reached an output").isZero();
	}

	private CaseResult runCase(EvalCase evalCase, Agents agents, PacedChatClient client) {
		UUID userId = UUID.randomUUID();
		List<String> forbidden = evalCase.expected().forbidden();
		List<String> leaks = new ArrayList<>();

		// 1. Extractor, scored against the labels.
		Timed<Extraction> extraction = timed(client, () -> agents.extractor().extract(evalCase.posting()));
		Step extractorStep;
		ExtractedPosting extracted = null;
		EvalMetrics.PrecisionRecall extractionScore = null;
		if (extraction.failure() == null) {
			extracted = extraction.value().posting();
			List<String> predicted = Stream
				.concat(extracted.requiredSkills().stream(), extracted.niceToHaveSkills().stream())
				.toList();
			List<String> gold = Stream.concat(evalCase.expected().requiredSkills().stream(),
					evalCase.expected().niceToHaveSkills().stream()).toList();
			extractionScore = EvalMetrics.precisionRecall(predicted, gold);
			leaks.addAll(EvalMetrics.leaks(jsonMapper.writeValueAsString(extracted), forbidden));
		}
		extractorStep = extraction.step();

		// 2. Matcher on the gold skills, scored against cvHas.
		ExtractedPosting gold = new ExtractedPosting(null, null, Seniority.UNSPECIFIED,
				evalCase.expected().requiredSkills(), evalCase.expected().niceToHaveSkills(), null, null, List.of(),
				null, "Gold skills of the eval case.");
		StepMeter matchMeter = new StepMeter(userId);
		Timed<MatchResult> match = timed(client, () -> agents.matcher().match(matchMeter, gold, evalCase.cv()));
		EvalMetrics.PrecisionRecall matchScore = null;
		if (match.failure() == null) {
			List<String> matched = match.value().matchedSkills().stream().map(MatchResult.MatchedSkill::skill).toList();
			matchScore = EvalMetrics.precisionRecall(matched, evalCase.expected().cvHas());
			leaks.addAll(EvalMetrics.leaks(String.join(", ", matched), forbidden));
		}

		// 3. Writer on the real extraction and match, as in the app.
		ExtractedPosting forWriter = extracted != null ? extracted : gold;
		MatchResult matchForWriter = match.failure() == null ? match.value()
				: new MatchResult(0, List.of(), List.of(), "No match available.", 0);
		StepMeter writeMeter = new StepMeter(userId);
		Timed<CoverLetterDraft> draft = timed(client, () -> agents.writer()
			.write(writeMeter, forWriter, evalCase.posting(), evalCase.cv(), matchForWriter, null, null));

		EvalReport.Letter letter = null;
		Timed<Review> review = null;
		Timed<EvalJudge.Verdict> verdict = null;
		Step writerStep = draft.step();
		Step reviewerStep = null;
		if (draft.failure() == null) {
			List<String> firstDraftNumbers = EvalMetrics.numbersNotIn(draft.value().letter(), evalCase.cv());
			leaks.addAll(EvalMetrics.leaks(draft.value().letter(), forbidden));

			// 4. The evaluator-optimizer loop, as in the app: review, revise while not approved.
			CoverLetterDraft current = draft.value();
			review = timed(client, () -> agents.reviewer()
				.review(new StepMeter(userId), forWriter, evalCase.posting(), evalCase.cv(), matchForWriter,
						draft.value()));
			reviewerStep = review.step();
			int revisions = 0;
			while (review.failure() == null && !review.value().approved() && revisions < MAX_REVISIONS) {
				CoverLetterDraft previous = current;
				Review feedback = review.value();
				Timed<CoverLetterDraft> revised = timed(client, () -> agents.writer()
					.write(new StepMeter(userId), forWriter, evalCase.posting(), evalCase.cv(), matchForWriter,
							previous, feedback));
				writerStep = writerStep.plus(revised.step());
				if (revised.failure() != null) {
					break;
				}
				revisions++;
				current = revised.value();
				leaks.addAll(EvalMetrics.leaks(current.letter(), forbidden));
				CoverLetterDraft toReview = current;
				review = timed(client, () -> agents.reviewer()
					.review(new StepMeter(userId), forWriter, evalCase.posting(), evalCase.cv(), matchForWriter,
							toReview));
				reviewerStep = reviewerStep.plus(review.step());
			}

			// 5. The final letter, as the user would get it, is checked by code and by the judge.
			String text = current.letter();
			String detected = PostingLanguage.detect(text);
			letter = new EvalReport.Letter(current.language(), detected,
					evalCase.language().equals(current.language()) && evalCase.language().equals(detected),
					EvalMetrics.wordCount(text), EvalMetrics.numbersNotIn(text, evalCase.cv()), firstDraftNumbers,
					revisions);
			verdict = timed(client, () -> agents.judge().judge(new StepMeter(userId), evalCase.cv(), text));
		}

		return new CaseResult(evalCase.id(), evalCase.language(), evalCase.tags(), extractorStep, extractionScore,
				match.step(), matchScore, match.failure() == null ? match.value().score() : null,
				match.failure() == null ? match.value().rejectedEvidence() : null,
				match.failure() == null ? matchMeter.toolCalls() : null, writerStep, letter, reviewerStep,
				review == null || review.failure() != null ? null : review.value().approved(),
				review == null || review.failure() != null ? null : review.value().fabricatedClaims().size(),
				verdict == null ? null : verdict.step(),
				verdict == null || verdict.failure() != null ? null
						: verdict.value().unsupportedClaims().stream().map(EvalJudge.Claim::claim).toList(),
				verdict == null || verdict.failure() != null ? null : verdict.value().discardedClaims(),
				leaks.stream().distinct().toList());
	}

	private List<EvalCase> select(List<EvalCase> all) {
		String only = System.getProperty("evals.only");
		if (only != null && !only.isBlank()) {
			List<String> ids = Arrays.stream(only.split(",")).map(String::strip).toList();
			return all.stream().filter(evalCase -> ids.contains(evalCase.id())).toList();
		}
		int limit = Integer.getInteger("evals.cases", all.size());
		return all.subList(0, Math.min(limit, all.size()));
	}

	/** Runs one agent, measuring latency and the calls and tokens it cost. */
	private static <T> Timed<T> timed(PacedChatClient client, Supplier<T> work) {
		PacedChatClient.Usage before = client.usage();
		long start = System.nanoTime();
		T value = null;
		String failure = null;
		try {
			value = work.get();
		}
		catch (AgentStepFailedException ex) {
			failure = ex.getReason().name();
		}
		catch (ExtractionFailedException ex) {
			failure = ex.getReason().name();
		}
		PacedChatClient.Usage cost = client.usage().minus(before);
		// Time spent waiting for the tokens-per-minute budget is not the agent's latency.
		long latencyMs = (System.nanoTime() - start) / 1_000_000 - cost.waitedMs();
		return new Timed<>(value, failure, new Step(failure == null, failure, latencyMs, cost.calls(),
				cost.promptTokens(), cost.completionTokens()));
	}

	private record Timed<T>(T value, String failure, Step step) {
	}

	private record Agents(LlmJobPostingExtractor extractor, MatcherAgent matcher, WriterAgent writer,
			ReviewerAgent reviewer, EvalJudge judge) {
	}

}
