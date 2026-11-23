package com.applypilot.agent;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * The Writer model's answer. Keep it in sync with {@code agent/cover-letter.schema.json}.
 *
 * @param language ISO 639-1 code of the language the letter is written in ("en", "es")
 * @param letter the letter as plain text
 */
public record CoverLetterDraft(
		@NotBlank @Pattern(regexp = "[a-z]{2}", message = "must be a two-letter ISO 639-1 code") String language,
		@NotBlank @Size(min = 200, max = 6000) String letter) {
}
