package com.applypilot.token;

import com.applypilot.common.error.ApiException;
import com.applypilot.token.dto.CreateTokenRequest;
import com.applypilot.token.dto.CreatedTokenResponse;
import com.applypilot.token.dto.TokenResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Personal access tokens for the MCP server: created, listed, revoked and checked.
 *
 * <p>A token is 32 random bytes (256 bits) with a recognizable {@code ap_pat_} prefix. Only its
 * SHA-256 hash is stored. A fast hash is enough here, unlike for passwords: nobody can guess
 * a 256-bit random value, so there is nothing for a slow hash to protect against, and a
 * stolen database still does not reveal any usable token.
 */
@Service
public class TokenService {

	static final String PREFIX = "ap_pat_";

	/** Enough for a few devices; more is probably forgotten tokens. */
	static final int MAX_ACTIVE_TOKENS = 5;

	private static final SecureRandom RANDOM = new SecureRandom();

	private final PersonalAccessTokenRepository tokens;

	private final Clock clock;

	public TokenService(PersonalAccessTokenRepository tokens, Clock clock) {
		this.tokens = tokens;
		this.clock = clock;
	}

	@Transactional
	public CreatedTokenResponse create(UUID userId, CreateTokenRequest request) {
		if (tokens.countByUserIdAndRevokedAtIsNull(userId) >= MAX_ACTIVE_TOKENS) {
			throw new TooManyTokensException();
		}
		byte[] secret = new byte[32];
		RANDOM.nextBytes(secret);
		String token = PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
		PersonalAccessToken saved = tokens.save(new PersonalAccessToken(userId, request.name().strip(), hash(token),
				token.substring(0, PREFIX.length() + 4), clock.instant()));
		return new CreatedTokenResponse(TokenResponse.from(saved), token);
	}

	@Transactional(readOnly = true)
	public List<TokenResponse> list(UUID userId) {
		return tokens.findByUserIdOrderByCreatedAtDesc(userId).stream().map(TokenResponse::from).toList();
	}

	/** Revokes the token; another user's token answers 404, like one that does not exist. */
	@Transactional
	public void revoke(UUID userId, UUID tokenId) {
		tokens.findByIdAndUserId(tokenId, userId)
			.orElseThrow(TokenNotFoundException::new)
			.revoke(clock.instant());
	}

	/**
	 * The user a raw token belongs to, if it is a valid, active token. Records the use.
	 */
	@Transactional
	public Optional<UUID> authenticate(String rawToken) {
		if (rawToken == null || !rawToken.startsWith(PREFIX) || rawToken.length() > 100) {
			return Optional.empty();
		}
		return tokens.findByTokenHashAndRevokedAtIsNull(hash(rawToken)).map(token -> {
			token.used(clock.instant());
			return token.getUserId();
		});
	}

	static String hash(String token) {
		try {
			return HexFormat.of()
				.formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 is always available in the JDK", ex);
		}
	}

	public static class TokenNotFoundException extends ApiException {

		public TokenNotFoundException() {
			super(HttpStatus.NOT_FOUND, "TOKEN_NOT_FOUND", "Token not found");
		}

	}

	public static class TooManyTokensException extends ApiException {

		public TooManyTokensException() {
			super(HttpStatus.CONFLICT, "TOO_MANY_TOKENS",
					"You already have " + MAX_ACTIVE_TOKENS + " active tokens. Revoke one you no longer use first.");
		}

	}

}
