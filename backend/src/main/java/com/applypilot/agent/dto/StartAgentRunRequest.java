package com.applypilot.agent.dto;

import com.applypilot.analysis.dto.AnalyzeRequest;
import jakarta.validation.constraints.Size;

/**
 * Optional body of a new match run.
 *
 * @param postingText new job posting text to analyze first; when absent the application's
 * stored analysis is reused. Same bounds as the analysis endpoint
 */
public record StartAgentRunRequest(
		@Size(min = AnalyzeRequest.MIN_LENGTH, max = AnalyzeRequest.MAX_LENGTH,
				message = "must be between 30 and 20000 characters") String postingText) {
}
