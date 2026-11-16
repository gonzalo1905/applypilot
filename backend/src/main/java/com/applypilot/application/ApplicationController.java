package com.applypilot.application;

import com.applypilot.application.dto.ApplicationResponse;
import com.applypilot.application.dto.CreateApplicationRequest;
import com.applypilot.application.dto.MoveApplicationRequest;
import com.applypilot.application.dto.UpdateApplicationRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Job applications of the authenticated user. The owner is always the token's subject;
 * no endpoint accepts a user id from the request.
 */
@RestController
@RequestMapping("/api/applications")
public class ApplicationController {

	private final ApplicationService applicationService;

	public ApplicationController(ApplicationService applicationService) {
		this.applicationService = applicationService;
	}

	@GetMapping
	public List<ApplicationResponse> list(@AuthenticationPrincipal Jwt jwt) {
		return applicationService.list(userId(jwt));
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public ApplicationResponse create(@AuthenticationPrincipal Jwt jwt,
			@Valid @RequestBody CreateApplicationRequest request) {
		return applicationService.create(userId(jwt), request);
	}

	@GetMapping("/{id}")
	public ApplicationResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
		return applicationService.get(userId(jwt), id);
	}

	@PutMapping("/{id}")
	public ApplicationResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
			@Valid @RequestBody UpdateApplicationRequest request) {
		return applicationService.update(userId(jwt), id, request);
	}

	@PatchMapping("/{id}/move")
	public ApplicationResponse move(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
			@Valid @RequestBody MoveApplicationRequest request) {
		return applicationService.move(userId(jwt), id, request);
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
		applicationService.delete(userId(jwt), id);
	}

	private static UUID userId(Jwt jwt) {
		return UUID.fromString(jwt.getSubject());
	}

}
