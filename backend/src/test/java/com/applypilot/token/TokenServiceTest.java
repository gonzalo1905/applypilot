package com.applypilot.token;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.applypilot.auth.DemoAccount;
import com.applypilot.token.dto.CreateTokenRequest;
import com.applypilot.token.dto.CreatedTokenResponse;
import com.applypilot.user.User;
import com.applypilot.user.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TokenServiceTest {

	private static final UUID USER_ID = UUID.randomUUID();

	private static final Instant NOW = Instant.parse("2026-11-23T10:00:00Z");

	@Mock
	private PersonalAccessTokenRepository tokens;

	@Mock
	private UserRepository users;

	private TokenService service() {
		return new TokenService(tokens, users, Clock.fixed(NOW, ZoneOffset.UTC));
	}

	@Test
	void theSharedDemoAccountCannotCreateTokens() {
		when(users.findById(USER_ID))
			.thenReturn(Optional.of(new User(DemoAccount.EMAIL, "unusable", DemoAccount.FULL_NAME)));

		assertThatThrownBy(() -> service().create(USER_ID, new CreateTokenRequest("Mine")))
			.isInstanceOf(TokenService.DemoTokensNotAllowedException.class);
		verify(tokens, never()).save(any());
	}

	@Test
	void createsARandomTokenAndStoresOnlyItsHash() {
		when(tokens.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		CreatedTokenResponse created = service().create(USER_ID, new CreateTokenRequest("  Claude Desktop "));

		assertThat(created.token()).startsWith("ap_pat_").hasSize(TokenService.PREFIX.length() + 43);
		ArgumentCaptor<PersonalAccessToken> saved = ArgumentCaptor.forClass(PersonalAccessToken.class);
		verify(tokens).save(saved.capture());
		assertThat(saved.getValue().getName()).isEqualTo("Claude Desktop");
		assertThat(saved.getValue().getTokenPrefix()).isEqualTo(created.token().substring(0, 11));
		assertThat(created.details().prefix()).isEqualTo(saved.getValue().getTokenPrefix());
		// The secret is never kept: neither in the entity nor in the response's toString.
		assertThat(saved.getValue().toString()).doesNotContain(created.token());
		assertThat(created.toString()).doesNotContain(created.token());
		assertThat(service().create(USER_ID, new CreateTokenRequest("Other")).token()).isNotEqualTo(created.token());
	}

	@Test
	void refusesMoreThanFiveActiveTokens() {
		when(tokens.countByUserIdAndRevokedAtIsNull(USER_ID)).thenReturn((long) TokenService.MAX_ACTIVE_TOKENS);

		assertThatThrownBy(() -> service().create(USER_ID, new CreateTokenRequest("Sixth")))
			.isInstanceOf(TokenService.TooManyTokensException.class);
		verify(tokens, never()).save(any());
	}

	@Test
	void authenticatesAnActiveTokenByItsHashAndRecordsTheUse() {
		String raw = "ap_pat_abcdefghijklmnopqrstuvwxyz0123456789ABCDEFG";
		PersonalAccessToken stored = new PersonalAccessToken(USER_ID, "Laptop", TokenService.hash(raw), "ap_pat_abcd",
				NOW.minusSeconds(60));
		when(tokens.findByTokenHashAndRevokedAtIsNull(TokenService.hash(raw))).thenReturn(Optional.of(stored));

		assertThat(service().authenticate(raw)).contains(USER_ID);
		assertThat(stored.getLastUsedAt()).isEqualTo(NOW);
	}

	@Test
	void rejectsWrongShapesWithoutQueryingTheDatabase() {
		assertThat(service().authenticate(null)).isEmpty();
		assertThat(service().authenticate("eyJhbGciOiJIUzI1NiJ9.a.b")).isEmpty();
		assertThat(service().authenticate("ap_pat_" + "x".repeat(200))).isEmpty();
		verify(tokens, never()).findByTokenHashAndRevokedAtIsNull(any());
	}

	@Test
	void anotherUsersTokenCannotBeRevoked() {
		UUID tokenId = UUID.randomUUID();
		when(tokens.findByIdAndUserId(tokenId, USER_ID)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service().revoke(USER_ID, tokenId))
			.isInstanceOf(TokenService.TokenNotFoundException.class);
	}

	@Test
	void revokingKeepsTheFirstRevocationTime() {
		PersonalAccessToken token = new PersonalAccessToken(USER_ID, "Laptop", "h".repeat(64), "ap_pat_abcd", NOW);
		token.revoke(NOW);
		token.revoke(NOW.plusSeconds(60));

		assertThat(token.getRevokedAt()).isEqualTo(NOW);
		assertThat(token.isActive()).isFalse();
	}

}
