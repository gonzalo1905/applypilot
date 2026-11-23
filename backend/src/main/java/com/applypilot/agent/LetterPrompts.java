package com.applypilot.agent;

import com.applypilot.analysis.ExtractedPosting;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.json.JsonMapper;

/**
 * Builds the material the Writer and the Reviewer both read. The orchestrator passes
 * structured data; each piece of untrusted text gets its own tag. Inputs are capped so one
 * call stays within the provider's per-minute token budget.
 */
final class LetterPrompts {

	/** Enough of the posting to see its language, tone and context. */
	static final int POSTING_CHARS = 3000;

	static final int CV_CHARS = 12_000;

	private LetterPrompts() {
	}

	static String posting(String postingText) {
		return UntrustedText.wrap("job_posting", UntrustedText.truncate(postingText, POSTING_CHARS));
	}

	static String requirements(JsonMapper jsonMapper, ExtractedPosting posting) {
		Map<String, Object> requirements = new LinkedHashMap<>();
		requirements.put("company", posting.company());
		requirements.put("role", posting.roleTitle());
		requirements.put("seniority", posting.seniority());
		requirements.put("required", posting.requiredSkills());
		requirements.put("niceToHave", posting.niceToHaveSkills());
		return UntrustedText.wrap("job_requirements", jsonMapper.writeValueAsString(requirements));
	}

	static String cv(String cvText) {
		return UntrustedText.wrap("cv", UntrustedText.truncate(cvText, CV_CHARS));
	}

	static String match(JsonMapper jsonMapper, MatchResult match) {
		Map<String, Object> summary = new LinkedHashMap<>();
		summary.put("matched", match.matchedSkills()
			.stream()
			.map(skill -> Map.of("skill", skill.skill(), "evidence", skill.evidence()))
			.toList());
		summary.put("missing", match.missingSkills().stream().map(MatchResult.MissingSkill::skill).toList());
		return UntrustedText.wrap("match_result", jsonMapper.writeValueAsString(summary));
	}

	static String join(List<String> parts) {
		return String.join("\n\n", parts);
	}

}
