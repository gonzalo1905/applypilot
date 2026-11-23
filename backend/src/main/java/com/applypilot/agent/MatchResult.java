package com.applypilot.agent;

import java.util.List;

/**
 * How well the CV covers the posting's skills.
 *
 * @param score 0 to 100, computed by our code from the verdicts (required skills weigh
 * double), never taken from the model, so it is explainable and repeatable
 * @param matchedSkills skills the CV shows, each with a quote that was checked to appear in
 * the CV
 * @param missingSkills skills the CV does not show (or whose evidence could not be verified)
 * @param summary two or three sentences for the user
 * @param rejectedEvidence how many "matched" verdicts were turned into "missing" because their
 * quote was not found in the CV
 */
public record MatchResult(int score, List<MatchedSkill> matchedSkills, List<MissingSkill> missingSkills,
		String summary, int rejectedEvidence) {

	public record MatchedSkill(String skill, boolean required, String evidence) {
	}

	public record MissingSkill(String skill, boolean required) {
	}

}
