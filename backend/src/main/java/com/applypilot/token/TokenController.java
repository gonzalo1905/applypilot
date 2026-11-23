package com.applypilot.token;

import com.applypilot.token.dto.CreateTokenRequest;
import com.applypilot.token.dto.CreatedTokenResponse;
import com.applypilot.token.dto.TokenResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The authenticated user's personal access tokens. Managed with the normal login token; a
 * personal access token itself only opens the read-only MCP endpoint.
 */
@RestController
@RequestMapping("/api/users/me/tokens")
public class TokenController {

	private final TokenService tokenService;

	public TokenController(TokenService tokenService) {
		this.tokenService = tokenService;
	}

	@GetMapping
	public List<TokenResponse> list(@AuthenticationPrincipal Jwt jwt) {
		return tokenService.list(userId(jwt));
	}

	/** Creates a token and returns it once; it cannot be shown again. */
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public CreatedTokenResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateTokenRequest request) {
		return tokenService.create(userId(jwt), request);
	}

	@DeleteMapping("/{tokenId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void revoke(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID tokenId) {
		tokenService.revoke(userId(jwt), tokenId);
	}

	private static UUID userId(Jwt jwt) {
		return UUID.fromString(jwt.getSubject());
	}

}
