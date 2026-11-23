package com.applypilot.analysis;

import com.applypilot.application.WorkMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * The structured data we want from a job posting. This record is the schema: the model's
 * JSON is parsed into it and then checked with Bean Validation, so anything that reaches the
 * rest of the application has the right shape and bounded sizes no matter what the model (or
 * a posting trying to manipulate it) produced.
 *
 * <p>Keep it in sync with {@code analysis/job-posting.schema.json}, the same schema in the
 * form the provider needs.
 */
public record ExtractedPosting(
		@Size(max = 160) String company,
		@Size(max = 200) String roleTitle,
		@NotNull Seniority seniority,
		@NotNull @Size(max = 40) List<@NotBlank @Size(max = 80) String> requiredSkills,
		@NotNull @Size(max = 40) List<@NotBlank @Size(max = 80) String> niceToHaveSkills,
		WorkMode workMode,
		@Size(max = 160) String location,
		@NotNull @Size(max = 10) List<@NotBlank @Size(max = 40) String> languages,
		@Size(max = 160) String salary,
		@NotBlank @Size(max = 1200) String summary) {
}
