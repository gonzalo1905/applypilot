package com.applypilot.analysis.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @param postingText the job posting as pasted by the user. The length cap bounds the cost
 * of one model call (longer text means more tokens) and what is stored
 */
public record AnalyzeRequest(
		@NotBlank @Size(min = AnalyzeRequest.MIN_LENGTH, max = AnalyzeRequest.MAX_LENGTH,
				message = "must be between 30 and 20000 characters") String postingText) {

	public static final int MIN_LENGTH = 30;

	public static final int MAX_LENGTH = 20_000;

}
