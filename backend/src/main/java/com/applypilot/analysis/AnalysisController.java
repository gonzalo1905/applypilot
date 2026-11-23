package com.applypilot.analysis;

import com.applypilot.analysis.dto.AnalysisResponse;
import com.applypilot.analysis.dto.AnalyzeRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The job posting analysis of one of the authenticated user's applications. An application
 * of another user answers 404, exactly like one that does not exist.
 */
@RestController
@RequestMapping("/api/applications/{applicationId}/analysis")
public class AnalysisController {

	private final AnalysisService analysisService;

	public AnalysisController(AnalysisService analysisService) {
		this.analysisService = analysisService;
	}

	@GetMapping
	public AnalysisResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID applicationId) {
		return analysisService.get(userId(jwt), applicationId);
	}

	/**
	 * Analyzes the posting and stores the result, replacing any earlier analysis. Answers 429
	 * when the user has used today's AI calls.
	 */
	@PostMapping
	public AnalysisResponse analyze(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID applicationId,
			@Valid @RequestBody AnalyzeRequest request) {
		return analysisService.analyze(userId(jwt), applicationId, request);
	}

	private static UUID userId(Jwt jwt) {
		return UUID.fromString(jwt.getSubject());
	}

}
