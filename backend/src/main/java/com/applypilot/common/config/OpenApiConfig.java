package com.applypilot.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * API documentation at {@code /swagger-ui.html}. Declares the bearer-token scheme so the
 * "Authorize" button in Swagger UI can send a JWT with every request.
 */
@Configuration
public class OpenApiConfig {

	private static final String BEARER_SCHEME = "bearerAuth";

	@Bean
	OpenAPI applyPilotOpenApi() {
		return new OpenAPI()
			.info(new Info().title("ApplyPilot API")
				.description("REST API for ApplyPilot, an AI-assisted job-application tracker.")
				.version("v1"))
			.components(new Components().addSecuritySchemes(BEARER_SCHEME,
					new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
			.addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
	}

}
