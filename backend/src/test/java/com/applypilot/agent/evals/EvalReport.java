package com.applypilot.agent.evals;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;

/** The result of one eval run, written as JSON (for tools) and Markdown (for people). */
public record EvalReport(int datasetVersion, String model, Instant startedAt, long durationSeconds,
		int rateLimitRetries, long pacingWaitSeconds, Summary summary, List<CaseResult> cases) {

	/** Latency and cost of one agent on one case. */
	public record Step(boolean ok, String failure, long latencyMs, int modelCalls, long promptTokens,
			long completionTokens) {
	}

	/**
	 * @param languageOk both the declared language and the detected language of the text match
	 * the posting's
	 * @param numbersNotInCv numbers in the letter that the CV does not contain
	 */
	public record Letter(String declaredLanguage, String detectedLanguage, boolean languageOk, int words,
			List<String> numbersNotInCv) {
	}

	public record CaseResult(String id, String language, List<String> tags, Step extractor,
			EvalMetrics.PrecisionRecall extraction, Step matcher, EvalMetrics.PrecisionRecall matching,
			Integer matchScore, Integer rejectedQuotes, Integer toolCalls, Step writer, Letter letter, Step reviewer,
			Boolean reviewerApproved, Integer reviewerFabricatedClaims, Step judge, List<String> judgeUnsupportedClaims,
			Integer judgeDiscardedClaims, List<String> leaks) {
	}

	/**
	 * @param validOutputRate steps that produced a validated answer (no fallback, no failure)
	 * @param lettersWithUnsupportedClaims letters where the judge found at least one claim
	 * @param reviewerJudgeAgreement cases where the Reviewer and the judge agree on whether the
	 * letter has unsupported claims
	 */
	public record Summary(int cases, double validOutputRate, double extractionPrecision, double extractionRecall,
			double matchingPrecision, double matchingRecall, double averageScoreStrongMatch,
			double averageScoreMismatch, int rejectedQuotes, double languageAccuracy, int lettersWithUnsupportedClaims,
			int lettersWithUnknownNumbers, double reviewerJudgeAgreement, int casesWithLeaks, int modelCalls,
			long promptTokens, long completionTokens, long averageExtractorMs, long averageMatcherMs,
			long averageWriterMs, long averageReviewerMs) {
	}

	static EvalReport of(int datasetVersion, String model, Instant started, Instant finished,
			List<CaseResult> cases, int rateLimitRetries, long waitedMs) {
		List<Step> steps = cases.stream()
			.flatMap(c -> java.util.stream.Stream.of(c.extractor(), c.matcher(), c.writer(), c.reviewer(), c.judge()))
			.filter(Objects::nonNull)
			.toList();
		List<Letter> letters = cases.stream().map(CaseResult::letter).filter(Objects::nonNull).toList();
		List<CaseResult> judged = cases.stream()
			.filter(c -> c.judgeUnsupportedClaims() != null && c.reviewerFabricatedClaims() != null)
			.toList();
		Summary summary = new Summary(cases.size(), rate(steps, Step::ok),
				average(cases, c -> c.extraction() == null ? null : c.extraction().precision()),
				average(cases, c -> c.extraction() == null ? null : c.extraction().recall()),
				average(cases, c -> c.matching() == null ? null : c.matching().precision()),
				average(cases, c -> c.matching() == null ? null : c.matching().recall()),
				average(cases.stream().filter(c -> c.tags().contains("strong-match")).toList(),
						c -> c.matchScore() == null ? null : c.matchScore().doubleValue()),
				average(cases.stream().filter(c -> c.tags().contains("mismatch")).toList(),
						c -> c.matchScore() == null ? null : c.matchScore().doubleValue()),
				cases.stream().map(CaseResult::rejectedQuotes).filter(Objects::nonNull).mapToInt(Integer::intValue).sum(),
				rate(letters, Letter::languageOk),
				(int) cases.stream()
					.filter(c -> c.judgeUnsupportedClaims() != null && !c.judgeUnsupportedClaims().isEmpty())
					.count(),
				(int) letters.stream().filter(l -> !l.numbersNotInCv().isEmpty()).count(),
				rate(judged, c -> (c.reviewerFabricatedClaims() > 0) == !c.judgeUnsupportedClaims().isEmpty()),
				(int) cases.stream().filter(c -> !c.leaks().isEmpty()).count(),
				steps.stream().mapToInt(Step::modelCalls).sum(), steps.stream().mapToLong(Step::promptTokens).sum(),
				steps.stream().mapToLong(Step::completionTokens).sum(), averageMs(cases, CaseResult::extractor),
				averageMs(cases, CaseResult::matcher), averageMs(cases, CaseResult::writer),
				averageMs(cases, CaseResult::reviewer));
		return new EvalReport(datasetVersion, model, started, Duration.between(started, finished).toSeconds(),
				rateLimitRetries, waitedMs / 1000, summary, cases);
	}

	String summaryLine() {
		Summary s = summary;
		return String.format(Locale.ROOT,
				"EVAL summary: cases=%d valid=%.0f%% extraction P/R=%.2f/%.2f matching P/R=%.2f/%.2f "
						+ "score strong/mismatch=%.0f/%.0f language=%.0f%% unsupportedClaims=%d unknownNumbers=%d "
						+ "reviewer~judge=%.0f%% leaks=%d calls=%d tokens=%d+%d time=%ds",
				s.cases(), s.validOutputRate() * 100, s.extractionPrecision(), s.extractionRecall(),
				s.matchingPrecision(), s.matchingRecall(), s.averageScoreStrongMatch(), s.averageScoreMismatch(),
				s.languageAccuracy() * 100, s.lettersWithUnsupportedClaims(), s.lettersWithUnknownNumbers(),
				s.reviewerJudgeAgreement() * 100, s.casesWithLeaks(), s.modelCalls(), s.promptTokens(),
				s.completionTokens(), durationSeconds);
	}

	String toMarkdown() {
		Summary s = summary;
		StringBuilder md = new StringBuilder();
		md.append("# Agent evals report\n\n");
		md.append("Dataset version ").append(datasetVersion).append(", model `").append(model).append("`, run ")
			.append(startedAt).append(", ").append(durationSeconds).append(" s (")
			.append(pacingWaitSeconds)
			.append(" s waiting for the tokens-per-minute budget, ")
			.append(rateLimitRetries)
			.append(" retries after 429).\n\n");
		md.append("## Summary\n\n| Metric | Value |\n|---|---|\n");
		row(md, "Cases", String.valueOf(s.cases()));
		row(md, "Steps with a valid answer (no fallback)", percent(s.validOutputRate()));
		row(md, "Extraction precision / recall (skills)", f2(s.extractionPrecision()) + " / " + f2(s.extractionRecall()));
		row(md, "Matching precision / recall (gold skills)", f2(s.matchingPrecision()) + " / " + f2(s.matchingRecall()));
		row(md, "Average score, strong-match cases", f0(s.averageScoreStrongMatch()));
		row(md, "Average score, mismatch cases", f0(s.averageScoreMismatch()));
		row(md, "Matcher quotes rejected (not in the CV)", String.valueOf(s.rejectedQuotes()));
		row(md, "Letters in the posting's language", percent(s.languageAccuracy()));
		row(md, "Letters with unsupported claims (judge)", String.valueOf(s.lettersWithUnsupportedClaims()));
		row(md, "Letters with numbers not in the CV (code)", String.valueOf(s.lettersWithUnknownNumbers()));
		row(md, "Reviewer agrees with the judge", percent(s.reviewerJudgeAgreement()));
		row(md, "Cases where injected text reached an output", String.valueOf(s.casesWithLeaks()));
		row(md, "Model calls", String.valueOf(s.modelCalls()));
		row(md, "Tokens (prompt + completion)", s.promptTokens() + " + " + s.completionTokens());
		row(md, "Average latency: extractor / matcher / writer / reviewer (ms)",
				s.averageExtractorMs() + " / " + s.averageMatcherMs() + " / " + s.averageWriterMs() + " / "
						+ s.averageReviewerMs());
		md.append("\n## Cases\n\n");
		md.append("| Case | Extraction P/R | Matching P/R | Score | Tools | Letter lang | Words | "
				+ "Numbers not in CV | Judge claims | Reviewer | Leaks | Failures |\n");
		md.append("|---|---|---|---|---|---|---|---|---|---|---|---|\n");
		for (CaseResult c : cases) {
			md.append("| ").append(c.id()).append(" | ")
				.append(c.extraction() == null ? "-" : f2(c.extraction().precision()) + "/" + f2(c.extraction().recall()))
				.append(" | ")
				.append(c.matching() == null ? "-" : f2(c.matching().precision()) + "/" + f2(c.matching().recall()))
				.append(" | ")
				.append(c.matchScore() == null ? "-" : c.matchScore())
				.append(" | ")
				.append(c.toolCalls() == null ? "-" : c.toolCalls())
				.append(" | ")
				.append(c.letter() == null ? "-"
						: c.letter().declaredLanguage() + "/" + c.letter().detectedLanguage()
								+ (c.letter().languageOk() ? " ok" : " WRONG"))
				.append(" | ")
				.append(c.letter() == null ? "-" : c.letter().words())
				.append(" | ")
				.append(c.letter() == null ? "-" : String.join(", ", c.letter().numbersNotInCv()))
				.append(" | ")
				.append(c.judgeUnsupportedClaims() == null ? "-" : c.judgeUnsupportedClaims().size())
				.append(" | ")
				.append(c.reviewerApproved() == null ? "-" : (c.reviewerApproved() ? "approved" : "changes"))
				.append(" | ")
				.append(c.leaks().isEmpty() ? "none" : String.join(", ", c.leaks()))
				.append(" | ")
				.append(failures(c))
				.append(" |\n");
		}
		md.append("\n## Misses\n\n");
		for (CaseResult c : cases) {
			if (c.extraction() != null
					&& (!c.extraction().falsePositives().isEmpty() || !c.extraction().falseNegatives().isEmpty())) {
				md.append("- ").append(c.id()).append(" extraction: extra ").append(c.extraction().falsePositives())
					.append(", missed ").append(c.extraction().falseNegatives()).append('\n');
			}
			if (c.matching() != null
					&& (!c.matching().falsePositives().isEmpty() || !c.matching().falseNegatives().isEmpty())) {
				md.append("- ").append(c.id()).append(" matching: wrongly matched ").append(c.matching().falsePositives())
					.append(", missed ").append(c.matching().falseNegatives()).append('\n');
			}
			if (c.judgeUnsupportedClaims() != null && !c.judgeUnsupportedClaims().isEmpty()) {
				md.append("- ").append(c.id()).append(" judge: ").append(c.judgeUnsupportedClaims()).append('\n');
			}
		}
		return md.toString();
	}

	private static String failures(CaseResult c) {
		List<String> failed = java.util.stream.Stream
			.of(name("extractor", c.extractor()), name("matcher", c.matcher()), name("writer", c.writer()),
					name("reviewer", c.reviewer()), name("judge", c.judge()))
			.filter(Objects::nonNull)
			.toList();
		return failed.isEmpty() ? "none" : String.join(", ", failed);
	}

	private static String name(String agent, Step step) {
		return step == null || step.ok() ? null : agent + " " + step.failure();
	}

	private static <T> double rate(List<T> items, Predicate<T> test) {
		return items.isEmpty() ? 0 : items.stream().filter(test).count() / (double) items.size();
	}

	private static double average(List<CaseResult> cases, Function<CaseResult, Double> value) {
		return cases.stream().map(value).filter(Objects::nonNull).mapToDouble(Double::doubleValue).average().orElse(0);
	}

	private static long averageMs(List<CaseResult> cases, Function<CaseResult, Step> step) {
		return (long) cases.stream()
			.map(step)
			.filter(Objects::nonNull)
			.mapToLong(Step::latencyMs)
			.average()
			.orElse(0);
	}

	private static void row(StringBuilder md, String metric, String value) {
		md.append("| ").append(metric).append(" | ").append(value).append(" |\n");
	}

	private static String percent(double value) {
		return String.format(Locale.ROOT, "%.0f%%", value * 100);
	}

	private static String f2(double value) {
		return String.format(Locale.ROOT, "%.2f", value);
	}

	private static String f0(double value) {
		return String.format(Locale.ROOT, "%.0f", value);
	}

}
