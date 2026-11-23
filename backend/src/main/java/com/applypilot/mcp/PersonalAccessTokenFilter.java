package com.applypilot.mcp;

import com.applypilot.token.TokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authenticates MCP requests with a personal access token ({@code Authorization: Bearer
 * ap_pat_...}). Not a Spring bean on purpose: as a bean, Spring Boot would also register it
 * for every request of the application, not only in the MCP security chain.
 */
class PersonalAccessTokenFilter extends OncePerRequestFilter {

	static final String READ_ONLY = "MCP_READ";

	private final TokenService tokens;

	PersonalAccessTokenFilter(TokenService tokens) {
		this.tokens = tokens;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String header = request.getHeader(HttpHeaders.AUTHORIZATION);
		if (header != null && header.regionMatches(true, 0, "Bearer ", 0, 7)) {
			tokens.authenticate(header.substring(7).strip()).ifPresent(userId -> {
				SecurityContext context = SecurityContextHolder.createEmptyContext();
				context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(new McpUser(userId), null,
						List.of(new SimpleGrantedAuthority(READ_ONLY))));
				SecurityContextHolder.setContext(context);
			});
		}
		chain.doFilter(request, response);
	}

}
