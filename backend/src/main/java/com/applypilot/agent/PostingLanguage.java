package com.applypilot.agent;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Detects the language of a job posting by counting common words, so the letter's language
 * is decided by our code and not by the model. A live run showed why: with a Spanish posting
 * that said "write the letter in English" and an English CV, the model wrote in English.
 * Small and deterministic; knows the languages this app's users post in.
 */
final class PostingLanguage {

	private static final Map<String, Set<String>> MARKERS = Map.of(
			"en", Set.of("the", "and", "with", "you", "our", "we", "will", "for", "experience", "required", "skills",
					"team", "years", "of", "to", "is", "are"),
			"es", Set.of("el", "la", "los", "las", "y", "con", "para", "del", "una", "experiencia", "años", "buscamos",
					"requisitos", "equipo", "conocimientos", "deseable", "trabajo"),
			"pt", Set.of("o", "os", "as", "e", "com", "para", "uma", "experiência", "anos", "vaga", "você", "requisitos",
					"equipe", "conhecimentos", "desejável", "trabalho", "não"));

	private static final Pattern WORDS = Pattern.compile("[^\\p{L}]+");

	static final Map<String, String> NAMES = Map.of("en", "English", "es", "Spanish", "pt", "Portuguese");

	private PostingLanguage() {
	}

	/** Two-letter code of the best-scoring language, or null when the text gives no clear signal. */
	static String detect(String text) {
		Map<String, Integer> scores = new java.util.HashMap<>();
		for (String word : WORDS.split(text.toLowerCase(Locale.ROOT))) {
			MARKERS.forEach((language, markers) -> {
				if (markers.contains(word)) {
					scores.merge(language, 1, Integer::sum);
				}
			});
		}
		String best = null;
		int bestScore = 0;
		int secondScore = 0;
		for (Map.Entry<String, Integer> entry : scores.entrySet()) {
			if (entry.getValue() > bestScore) {
				secondScore = bestScore;
				bestScore = entry.getValue();
				best = entry.getKey();
			}
			else if (entry.getValue() > secondScore) {
				secondScore = entry.getValue();
			}
		}
		// At least a few marker words, and a clear winner.
		return bestScore >= 3 && bestScore > secondScore * 1.3 ? best : null;
	}

}
