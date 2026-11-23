package com.applypilot.common.config;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * CORS settings bound from {@code app.cors.*}.
 */
@Validated
@ConfigurationProperties(prefix = "app.cors")
public record CorsProperties(
		// A missing variable would otherwise leave the literal "${...}" here and start an
		// application whose browser calls are all refused.
		@NotEmpty List<@Pattern(regexp = "https?://[^\\s$]+", message = "must be an http(s) origin") String> allowedOrigins) {
}
