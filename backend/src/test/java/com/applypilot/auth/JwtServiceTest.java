package com.applypilot.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.applypilot.user.User;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Round-trips a token through the real encoder and decoder (no Spring context needed).
 */
class JwtServiceTest {

	private static final JwtProperties PROPERTIES = new JwtProperties("unit-test-secret-with-at-least-32-chars!", 60);

	private final JwtConfig jwtConfig = new JwtConfig();

	private final JwtService jwtService = new JwtService(jwtConfig.jwtEncoder(PROPERTIES), PROPERTIES);

	@Test
	void generatedTokenCarriesUserIdAsSubjectAndIsAcceptedByTheDecoder() {
		User user = userWithId(UUID.randomUUID());

		Jwt jwt = jwtConfig.jwtDecoder(PROPERTIES).decode(jwtService.generateToken(user));

		assertThat(jwt.getSubject()).isEqualTo(user.getId().toString());
		assertThat(jwt.getClaimAsString("email")).isEqualTo("ada@example.com");
		assertThat(jwt.getClaimAsString("iss")).isEqualTo(JwtConfig.ISSUER);
		assertThat(jwt.getExpiresAt()).isEqualTo(jwt.getIssuedAt().plusSeconds(jwtService.expiresInSeconds()));
	}

	@Test
	void tokenSignedWithAnotherSecretIsRejected() {
		String token = jwtService.generateToken(userWithId(UUID.randomUUID()));
		JwtDecoder otherDecoder = jwtConfig
			.jwtDecoder(new JwtProperties("a-completely-different-secret-of-32-chars", 60));

		assertThatThrownBy(() -> otherDecoder.decode(token)).isInstanceOf(JwtException.class);
	}

	private static User userWithId(UUID id) {
		User user = new User("ada@example.com", "hashed", "Ada Lovelace");
		// The id is normally assigned by JPA when the entity is persisted.
		ReflectionTestUtils.setField(user, "id", id);
		return user;
	}

}
