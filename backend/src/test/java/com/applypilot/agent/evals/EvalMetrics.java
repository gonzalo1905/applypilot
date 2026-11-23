package com.applypilot.agent.evals;

import com.applypilot.agent.LetterFacts;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The deterministic checks of the evals. Cheap, repeatable and impossible to argue with, so
 * they are preferred to a model judge wherever they can decide the question.
 */
public final class EvalMetrics {

	private static final Pattern NON_SKILL_CHARS = Pattern.compile("[^\\p{L}\\p{N}+#]+");

	private EvalMetrics() {
	}

	/** Precision and recall of predicted items against gold labels. */
	public record PrecisionRecall(double precision, double recall, List<String> falsePositives,
			List<String> falseNegatives) {
	}

	/**
	 * Skill names compared loosely: case, punctuation and versions do not matter, and one name
	 * may contain the other ("Java 21" is "Java", "REST APIs" is "APIs REST").
	 */
	public static boolean sameSkill(String predicted, String gold) {
		Set<String> a = tokens(predicted);
		Set<String> b = tokens(gold);
		if (a.isEmpty() || b.isEmpty()) {
			return false;
		}
		return a.containsAll(b) || b.containsAll(a) || compact(predicted).equals(compact(gold));
	}

	public static PrecisionRecall precisionRecall(List<String> predicted, List<String> gold) {
		List<String> falsePositives = new ArrayList<>();
		for (String item : predicted) {
			if (gold.stream().noneMatch(label -> sameSkill(item, label))) {
				falsePositives.add(item);
			}
		}
		List<String> falseNegatives = new ArrayList<>();
		for (String label : gold) {
			if (predicted.stream().noneMatch(item -> sameSkill(item, label))) {
				falseNegatives.add(label);
			}
		}
		double precision = predicted.isEmpty() ? (gold.isEmpty() ? 1 : 0)
				: (predicted.size() - falsePositives.size()) / (double) predicted.size();
		double recall = gold.isEmpty() ? 1 : (gold.size() - falseNegatives.size()) / (double) gold.size();
		return new PrecisionRecall(precision, recall, falsePositives, falseNegatives);
	}

	/** Forbidden strings present in the text, ignoring case. */
	public static List<String> leaks(String text, List<String> forbidden) {
		if (text == null) {
			return List.of();
		}
		String lower = text.toLowerCase(Locale.ROOT);
		return forbidden.stream().filter(item -> lower.contains(item.toLowerCase(Locale.ROOT))).toList();
	}

	/**
	 * Numbers in the letter that the CV does not contain. A number is the most common
	 * invented fact (years, percentages, team sizes), and code can check it exactly. Numbers
	 * written as words ("six years") are not seen; the judge covers those.
	 */
	public static List<String> numbersNotIn(String letter, String cv) {
		// The same check the Reviewer now runs in production: the evals found the problem.
		return LetterFacts.numbersNotIn(letter, cv);
	}

	public static int wordCount(String text) {
		String trimmed = text == null ? "" : text.strip();
		return trimmed.isEmpty() ? 0 : trimmed.split("\\s+").length;
	}

	/** True when the quote, ignoring case and spacing, is part of the text. */
	public static boolean containsQuote(String text, String quote) {
		String normalized = normalizeSpaces(quote).replaceAll("^[\"'“”‘’.…\\s]+|[\"'“”‘’.…\\s]+$", "");
		return !normalized.isEmpty() && normalizeSpaces(text).contains(normalized);
	}

	private static Set<String> tokens(String skill) {
		String cleaned = NON_SKILL_CHARS.matcher(skill.toLowerCase(Locale.ROOT)).replaceAll(" ").strip();
		Set<String> tokens = new LinkedHashSet<>(Arrays.asList(cleaned.split(" ")));
		tokens.remove("");
		// Versions and plural "s" do not make a different skill.
		tokens.removeIf(token -> token.matches("\\d+(\\.\\d+)*"));
		Set<String> singular = new LinkedHashSet<>();
		for (String token : tokens) {
			singular.add(token.length() > 3 && token.endsWith("s") ? token.substring(0, token.length() - 1) : token);
		}
		return singular;
	}

	private static String compact(String skill) {
		return NON_SKILL_CHARS.matcher(skill.toLowerCase(Locale.ROOT)).replaceAll("");
	}

	private static String normalizeSpaces(String text) {
		return text == null ? "" : text.replaceAll("\\s+", " ").strip().toLowerCase(Locale.ROOT);
	}

}
