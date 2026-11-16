package com.applypilot.application.dto;

import com.applypilot.application.ApplicationStatus;
import com.applypilot.application.WorkMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * @param status the column the new card goes into; defaults to {@code SAVED} when omitted
 */
public record CreateApplicationRequest(
		@NotBlank @Size(max = 120) String company,
		@NotBlank @Size(max = 160) String roleTitle,
		@Size(max = 2048) @Pattern(regexp = "^https?://\\S+$", message = "must be an http or https URL") String jobUrl,
		@Size(max = 120) String location,
		WorkMode workMode,
		@Size(max = 120) String salaryNote,
		@Size(max = 5000) String notes,
		ApplicationStatus status,
		LocalDate appliedAt) implements ApplicationDetails {
}
