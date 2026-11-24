package com.applypilot.insights;

import com.applypilot.insights.dto.InsightsResponse;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Board summary for the authenticated user. Only ever reads the caller's own data. */
@RestController
@RequestMapping("/api/insights")
public class InsightsController {

	private final InsightsService insightsService;

	public InsightsController(InsightsService insightsService) {
		this.insightsService = insightsService;
	}

	@GetMapping
	public InsightsResponse get(@AuthenticationPrincipal Jwt jwt) {
		return insightsService.get(UUID.fromString(jwt.getSubject()));
	}

}
