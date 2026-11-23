package com.applypilot.mcp;

import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * The user an MCP request acts for, set by {@link PersonalAccessTokenFilter}. Tools read it
 * here and never take a user id as an argument, so a model cannot ask for someone else's data.
 */
public record McpUser(UUID userId) {

	/** The current MCP user; fails closed when there is none. */
	static UUID require() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication != null && authentication.getPrincipal() instanceof McpUser user) {
			return user.userId();
		}
		throw new IllegalStateException("No authenticated MCP user");
	}

}
