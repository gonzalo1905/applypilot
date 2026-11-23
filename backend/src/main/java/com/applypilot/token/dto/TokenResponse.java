package com.applypilot.token.dto;

import com.applypilot.token.PersonalAccessToken;
import java.time.Instant;
import java.util.UUID;

/** A token as listed: never the secret, only its prefix. */
public record TokenResponse(UUID id, String name, String prefix, Instant createdAt, Instant lastUsedAt,
		Instant revokedAt) {

	public static TokenResponse from(PersonalAccessToken token) {
		return new TokenResponse(token.getId(), token.getName(), token.getTokenPrefix(), token.getCreatedAt(),
				token.getLastUsedAt(), token.getRevokedAt());
	}

}
