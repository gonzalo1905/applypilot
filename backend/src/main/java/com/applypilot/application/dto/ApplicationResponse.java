package com.applypilot.application.dto;

import com.applypilot.application.ApplicationStatus;
import com.applypilot.application.JobApplication;
import com.applypilot.application.WorkMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ApplicationResponse(
		UUID id,
		String company,
		String roleTitle,
		String jobUrl,
		String location,
		WorkMode workMode,
		String salaryNote,
		String notes,
		ApplicationStatus status,
		int position,
		LocalDate appliedAt,
		Instant createdAt,
		Instant updatedAt) {

	public static ApplicationResponse from(JobApplication application) {
		return new ApplicationResponse(application.getId(), application.getCompany(), application.getRoleTitle(),
				application.getJobUrl(), application.getLocation(), application.getWorkMode(),
				application.getSalaryNote(), application.getNotes(), application.getStatus(),
				application.getPosition(), application.getAppliedAt(), application.getCreatedAt(),
				application.getUpdatedAt());
	}

}
