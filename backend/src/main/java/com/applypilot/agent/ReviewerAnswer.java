package com.applypilot.agent;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** The Reviewer model's answer. Keep it in sync with {@code agent/review.schema.json}. */
public record ReviewerAnswer(
		@NotNull Boolean approved,
		@NotNull @Size(max = 10) List<@NotNull @Valid FabricatedClaim> fabricatedClaims,
		@NotNull @Size(max = 10) List<@NotBlank @Size(max = 300) String> missingRequirements,
		@NotNull @Size(max = 5) List<@NotBlank @Size(max = 300) String> toneIssues,
		@NotNull @Size(max = 5) List<@NotBlank @Size(max = 300) String> suggestions) {

	public record FabricatedClaim(@NotBlank @Size(max = 400) String claim, @NotBlank @Size(max = 300) String reason) {
	}

}
