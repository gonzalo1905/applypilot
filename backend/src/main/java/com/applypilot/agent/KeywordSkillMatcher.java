package com.applypilot.agent;

import com.applypilot.agent.MatcherAnswer.SkillVerdict;
import com.applypilot.analysis.ExtractedPosting;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

/**
 * The Matcher's fallback: looks for each skill name as a whole word in the CV. Deterministic,
 * free and unable to fail, but blind to synonyms ("Postgres" does not match "PostgreSQL").
 * Its evidence is the CV line where the word was found, so it is always a real quote.
 */
@Component
public class KeywordSkillMatcher {

	public MatchResult match(ExtractedPosting posting, String cvText) {
		List<SkillVerdict> verdicts = new ArrayList<>();
		int requiredFound = 0;
		for (String skill : Stream.concat(posting.requiredSkills().stream(), posting.niceToHaveSkills().stream())
			.toList()) {
			String line = findLine(skill, cvText);
			verdicts.add(new SkillVerdict(skill, line != null, line));
			if (line != null && posting.requiredSkills().contains(skill)) {
				requiredFound++;
			}
		}
		String summary = "Keyword comparison without AI: " + requiredFound + " of "
				+ posting.requiredSkills().size()
				+ " required skills appear by name in the CV. Synonyms and related experience are not recognized.";
		return MatchScoring.score(posting.requiredSkills(), posting.niceToHaveSkills(), verdicts, summary, cvText);
	}

	/** The first CV line containing the skill as a whole word, cut to 200 characters. */
	static String findLine(String skill, String cvText) {
		Pattern word = Pattern.compile("(?iu)(?<![\\p{L}\\p{N}])" + Pattern.quote(skill.strip()) + "(?![\\p{L}\\p{N}])");
		for (String line : cvText.split("\\R")) {
			String compact = line.strip().replaceAll("\\s+", " ");
			Matcher matcher = word.matcher(compact);
			if (matcher.find()) {
				int start = Math.max(0, matcher.start() - 80);
				int end = Math.min(compact.length(), start + 200);
				return compact.substring(start, end).strip();
			}
		}
		return null;
	}

}
