package com.applypilot.mcp;

import com.applypilot.token.TokenService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;

/**
 * Security for the MCP endpoint, separate from the API's: it accepts only personal access
 * tokens (not login tokens), and those tokens open nothing but this endpoint. Ordered before
 * the main chain so it handles {@code /mcp} itself.
 */
@Configuration
public class McpSecurityConfig {

	@Bean
	@Order(1)
	SecurityFilterChain mcpSecurityFilterChain(HttpSecurity http, TokenService tokens) throws Exception {
		http.securityMatcher("/mcp", "/mcp/**")
			.csrf(AbstractHttpConfigurer::disable)
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.addFilterBefore(new PersonalAccessTokenFilter(tokens), AuthorizationFilter.class)
			.authorizeHttpRequests(auth -> auth.anyRequest().hasAuthority(PersonalAccessTokenFilter.READ_ONLY))
			.exceptionHandling(exceptions -> exceptions.authenticationEntryPoint((request, response, ex) -> {
				response.setHeader("WWW-Authenticate", "Bearer realm=\"applypilot-mcp\"");
				response.sendError(HttpStatus.UNAUTHORIZED.value(),
						"Use a personal access token from your ApplyPilot profile");
			}));
		return http.build();
	}

}
