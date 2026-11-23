package com.applypilot.analysis.dto;

import com.applypilot.analysis.AnalysisSource;
import com.applypilot.analysis.FallbackReason;
import com.applypilot.analysis.JobAnalysis;
import com.applypilot.analysis.Seniority;
import com.applypilot.application.WorkMode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * @param source who produced the result: the language model or the keyword fallback
 * @param fallbackReason why the fallback was used; null when {@code source} is LLM
 * @param cached true when this result was reused from an earlier analysis of the same text
 * instead of calling the model again
 */
public record AnalysisResponse(
		UUID id,
		UUID applicationId,
		String postingText,
		String company,
		String roleTitle,
		Seniority seniority,
		List<String> requiredSkills,
		List<String> niceToHaveSkills,
		WorkMode workMode,
		String location,
		List<String> languages,
		String salary,
		String summary,
		AnalysisSource source,
		FallbackReason fallbackReason,
		String model,
		boolean cached,
		Instant analyzedAt) {

	public static AnalysisResponse from(JobAnalysis analysis, boolean cached) {
		return new AnalysisResponse(analysis.getId(), analysis.getApplicationId(), analysis.getPostingText(),
				analysis.getCompany(), analysis.getRoleTitle(), analysis.getSeniority(),
				List.copyOf(analysis.getRequiredSkills()), List.copyOf(analysis.getNiceToHaveSkills()),
				analysis.getWorkMode(), analysis.getLocation(), List.copyOf(analysis.getLanguages()),
				analysis.getSalary(), analysis.getSummary(), analysis.getSource(), analysis.getFallbackReason(),
				analysis.getModel(), cached, analysis.getAnalyzedAt());
	}

}
