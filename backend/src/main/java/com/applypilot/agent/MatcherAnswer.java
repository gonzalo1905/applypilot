package com.applypilot.agent;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * The Matcher model's answer: one verdict per skill and a short summary. Keep it in sync
 * with {@code agent/matcher-answer.schema.json}.
 */
public record MatcherAnswer(
		@NotNull @Size(max = 80) List<@NotNull @Valid SkillVerdict> skills,
		@NotBlank @Size(max = 600) String summary) {

	/**
	 * @param evidence a short quote copied from the CV; required when {@code matched} is true
	 */
	public record SkillVerdict(
			@NotBlank @Size(max = 80) String skill,
			@NotNull Boolean matched,
			@Size(max = 300) String evidence) {
	}

}
