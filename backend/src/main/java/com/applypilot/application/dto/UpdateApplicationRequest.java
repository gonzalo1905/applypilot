package com.applypilot.application.dto;

import com.applypilot.application.WorkMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * Replaces the editable details of a card. Status and position are not here on purpose:
 * they only change through the move endpoint, which keeps the column order consistent.
 */
public record UpdateApplicationRequest(
		@NotBlank @Size(max = 120) String company,
		@NotBlank @Size(max = 160) String roleTitle,
		@Size(max = 2048) @Pattern(regexp = "^\\s*$|^https?://\\S+$", message = "must be an http or https URL") String jobUrl,
		@Size(max = 120) String location,
		WorkMode workMode,
		@Size(max = 120) String salaryNote,
		@Size(max = 5000) String notes,
		LocalDate appliedAt) implements ApplicationDetails {
}
