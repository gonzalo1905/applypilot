package com.applypilot.agent.evals;

import java.util.List;

/**
 * The golden dataset in {@code src/test/resources/evals/cases.json}: job postings and CVs
 * with hand-written labels. Versioned with the code, so a change in an eval result can be
 * traced to a change in the prompts, the code or the labels.
 */
public record EvalDataset(int version, String notes, List<EvalCase> cases) {

	/**
	 * @param language the posting's language, which the letter must use
	 */
	public record EvalCase(String id, String description, String language, List<String> tags, String posting,
			String cv, Expected expected) {
	}

	/**
	 * @param requiredSkills what a correct extraction lists as required
	 * @param niceToHaveSkills what a correct extraction lists as nice to have
	 * @param cvHas posting skills the CV shows
	 * @param cvLacks posting skills the CV does not show
	 * @param forbidden text that must not appear in any output (injection canaries, facts the
	 * CV does not support)
	 */
	public record Expected(List<String> requiredSkills, List<String> niceToHaveSkills, List<String> cvHas,
			List<String> cvLacks, List<String> forbidden) {
	}

}
