package com.applypilot.agent;

import com.applypilot.agent.MatchResult.MatchedSkill;
import com.applypilot.agent.MatchResult.MissingSkill;
import com.applypilot.agent.MatcherAnswer.SkillVerdict;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Turns per-skill verdicts into a {@link MatchResult}: checks every quote against the CV and
 * computes the score. The model judges; this code verifies and counts.
 */
final class MatchScoring {

	private MatchScoring() {
	}

	/**
	 * @param verdicts the model's (or the keyword matcher's) verdicts; skills not in the
	 * posting are ignored, posting skills without a verdict count as missing
	 */
	static MatchResult score(List<String> requiredSkills, List<String> niceToHaveSkills, List<SkillVerdict> verdicts,
			String summary, String cvText) {
		Map<String, SkillVerdict> byKey = new HashMap<>();
		for (SkillVerdict verdict : verdicts) {
			byKey.putIfAbsent(key(verdict.skill()), verdict);
		}
		String searchableCv = normalize(cvText);
		List<MatchedSkill> matched = new ArrayList<>();
		List<MissingSkill> missing = new ArrayList<>();
		int rejected = 0;
		int points = 0;
		int maxPoints = 0;
		for (Skill skill : skills(requiredSkills, niceToHaveSkills)) {
			int weight = skill.required() ? 2 : 1;
			maxPoints += weight;
			SkillVerdict verdict = byKey.get(key(skill.name()));
			if (verdict == null || !Boolean.TRUE.equals(verdict.matched())) {
				missing.add(new MissingSkill(skill.name(), skill.required()));
			}
			else if (isQuoteOf(verdict.evidence(), searchableCv)) {
				matched.add(new MatchedSkill(skill.name(), skill.required(), verdict.evidence().strip()));
				points += weight;
			}
			else {
				// A "match" without a quote we can find in the CV is not trusted.
				missing.add(new MissingSkill(skill.name(), skill.required()));
				rejected++;
			}
		}
		int score = maxPoints == 0 ? 0 : Math.round(100f * points / maxPoints);
		return new MatchResult(score, matched, missing, summary, rejected);
	}

	/** True if the quote, ignoring case, spacing and surrounding quote marks, is in the CV. */
	static boolean isQuoteOf(String evidence, String normalizedCv) {
		if (evidence == null) {
			return false;
		}
		String quote = normalize(evidence).replaceAll("^[\"'“”‘’.…\\s]+|[\"'“”‘’.…\\s]+$", "");
		return quote.length() >= 3 && normalizedCv.contains(quote);
	}

	static String normalize(String text) {
		return text == null ? "" : text.replaceAll("\\s+", " ").strip().toLowerCase(Locale.ROOT);
	}

	private static List<Skill> skills(List<String> required, List<String> niceToHave) {
		Map<String, Skill> unique = new java.util.LinkedHashMap<>();
		required.forEach(name -> unique.putIfAbsent(key(name), new Skill(name, true)));
		niceToHave.forEach(name -> unique.putIfAbsent(key(name), new Skill(name, false)));
		return List.copyOf(unique.values());
	}

	private static String key(String skill) {
		return skill == null ? "" : skill.strip().toLowerCase(Locale.ROOT);
	}

	private record Skill(String name, boolean required) {
	}

}
