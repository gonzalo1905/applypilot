package com.applypilot.auth;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * JWT settings bound from {@code app.jwt.*}. Validated at startup, so a missing or
 * too-short secret stops the application instead of failing on the first login.
 */
@Validated
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
		@NotBlank @Size(min = 32, message = "must be at least 32 characters (256 bits) for HS256") String secret,
		@Min(1) long expirationMinutes) {
}
