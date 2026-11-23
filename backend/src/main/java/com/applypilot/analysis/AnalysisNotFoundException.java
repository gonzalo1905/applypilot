package com.applypilot.analysis;

import com.applypilot.common.error.ApiException;
import org.springframework.http.HttpStatus;

public class AnalysisNotFoundException extends ApiException {

	public AnalysisNotFoundException() {
		super(HttpStatus.NOT_FOUND, "ANALYSIS_NOT_FOUND", "This application has no job posting analysis yet");
	}

}
