package com.applypilot.agent;

import com.applypilot.agent.dto.AgentRunResponse;
import com.applypilot.agent.dto.StartAgentRunRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Match runs (the "Match and cover letter" view) of one of the authenticated user's
 * applications. An application of another user answers 404, exactly like one that does not
 * exist.
 */
@RestController
@RequestMapping("/api/applications/{applicationId}/agent-runs")
public class AgentRunController {

	private final AgentRunService agentRunService;

	public AgentRunController(AgentRunService agentRunService) {
		this.agentRunService = agentRunService;
	}

	/**
	 * Queues a run and answers 202 right away; poll {@code GET .../latest} for progress. The
	 * body is optional: without posting text the stored analysis is reused.
	 */
	@PostMapping
	@ResponseStatus(HttpStatus.ACCEPTED)
	public AgentRunResponse start(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID applicationId,
			@Valid @RequestBody(required = false) StartAgentRunRequest request) {
		return agentRunService.start(userId(jwt), applicationId, request);
	}

	@GetMapping("/latest")
	public AgentRunResponse latest(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID applicationId) {
		return agentRunService.latest(userId(jwt), applicationId);
	}

	private static UUID userId(Jwt jwt) {
		return UUID.fromString(jwt.getSubject());
	}

}
