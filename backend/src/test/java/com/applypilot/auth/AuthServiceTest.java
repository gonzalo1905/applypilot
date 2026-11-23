package com.applypilot.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.applypilot.auth.dto.AuthResponse;
import com.applypilot.auth.dto.LoginRequest;
import com.applypilot.auth.dto.RegisterRequest;
import com.applypilot.common.error.InvalidRequestException;
import com.applypilot.user.User;
import com.applypilot.user.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

	@Mock
	private UserRepository userRepository;

	@Mock
	private PasswordEncoder passwordEncoder;

	@Mock
	private JwtService jwtService;

	private AuthService authService;

	@BeforeEach
	void openAccess() {
		authService = new AuthService(userRepository, passwordEncoder, jwtService, new AccessProperties(true, true));
	}

	@Test
	void registerHashesPasswordNormalizesEmailAndReturnsToken() {
		when(userRepository.existsByEmail("ada@example.com")).thenReturn(false);
		when(passwordEncoder.encode("s3cret-password")).thenReturn("hashed");
		when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
		when(jwtService.generateToken(any(User.class))).thenReturn("jwt-token");
		when(jwtService.expiresInSeconds()).thenReturn(3600L);

		AuthResponse response = authService
			.register(new RegisterRequest("  Ada@Example.COM ", "s3cret-password", " Ada Lovelace "));

		ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
		verify(userRepository).saveAndFlush(saved.capture());
		assertThat(saved.getValue().getEmail()).isEqualTo("ada@example.com");
		assertThat(saved.getValue().getPasswordHash()).isEqualTo("hashed");
		assertThat(saved.getValue().getFullName()).isEqualTo("Ada Lovelace");

		assertThat(response.accessToken()).isEqualTo("jwt-token");
		assertThat(response.tokenType()).isEqualTo("Bearer");
		assertThat(response.expiresIn()).isEqualTo(3600L);
		assertThat(response.user().email()).isEqualTo("ada@example.com");
		assertThat(response.user().fullName()).isEqualTo("Ada Lovelace");
	}

	@Test
	void registerRejectsEmailThatIsAlreadyUsed() {
		when(userRepository.existsByEmail("ada@example.com")).thenReturn(true);

		assertThatThrownBy(
				() -> authService.register(new RegisterRequest("ada@example.com", "s3cret-password", "Ada Lovelace")))
			.isInstanceOf(EmailAlreadyUsedException.class);

		verify(userRepository, never()).saveAndFlush(any());
		verify(passwordEncoder, never()).encode(anyString());
	}

	@Test
	void registerTranslatesUniqueConstraintViolationWhenTwoRequestsRace() {
		when(userRepository.existsByEmail("ada@example.com")).thenReturn(false);
		when(passwordEncoder.encode("s3cret-password")).thenReturn("hashed");
		when(userRepository.saveAndFlush(any(User.class))).thenThrow(new DataIntegrityViolationException("duplicate"));

		assertThatThrownBy(
				() -> authService.register(new RegisterRequest("ada@example.com", "s3cret-password", "Ada Lovelace")))
			.isInstanceOf(EmailAlreadyUsedException.class);
	}

	@Test
	void registerRejectsPasswordLongerThanBcryptLimit() {
		// 40 characters, but 80 bytes in UTF-8: passes a character-count check, exceeds BCrypt's limit.
		String password = "ñ".repeat(40);

		assertThatThrownBy(() -> authService.register(new RegisterRequest("ada@example.com", password, "Ada Lovelace")))
			.isInstanceOf(InvalidRequestException.class);

		verify(userRepository, never()).saveAndFlush(any());
	}

	@Test
	void loginReturnsTokenWhenCredentialsMatch() {
		User user = new User("ada@example.com", "hashed", "Ada Lovelace");
		when(userRepository.findByEmail("ada@example.com")).thenReturn(Optional.of(user));
		when(passwordEncoder.matches("s3cret-password", "hashed")).thenReturn(true);
		when(jwtService.generateToken(user)).thenReturn("jwt-token");
		when(jwtService.expiresInSeconds()).thenReturn(3600L);

		AuthResponse response = authService.login(new LoginRequest(" ADA@example.com", "s3cret-password"));

		assertThat(response.accessToken()).isEqualTo("jwt-token");
		assertThat(response.user().email()).isEqualTo("ada@example.com");
	}

	@Test
	void loginRejectsUnknownEmail() {
		when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> authService.login(new LoginRequest("nobody@example.com", "whatever")))
			.isInstanceOf(InvalidCredentialsException.class);

		verify(jwtService, never()).generateToken(any());
	}

	@Test
	void loginRejectsWrongPasswordWithTheSameErrorAsUnknownEmail() {
		User user = new User("ada@example.com", "hashed", "Ada Lovelace");
		when(userRepository.findByEmail("ada@example.com")).thenReturn(Optional.of(user));
		when(passwordEncoder.matches("wrong-password", "hashed")).thenReturn(false);

		assertThatThrownBy(() -> authService.login(new LoginRequest("ada@example.com", "wrong-password")))
			.isInstanceOf(InvalidCredentialsException.class)
			.hasMessage("Invalid email or password");

		verify(jwtService, never()).generateToken(any());
	}

	@Test
	void registrationCanBeClosed() {
		AuthService closed = new AuthService(userRepository, passwordEncoder, jwtService,
				new AccessProperties(false, true));

		assertThatThrownBy(() -> closed.register(new RegisterRequest("ada@example.com", "s3cret-password", "Ada")))
			.isInstanceOf(AccessDeniedExceptions.RegistrationClosedException.class);
		verify(userRepository, never()).saveAndFlush(any());
	}

	@Test
	void theDemoLoginIssuesATokenForTheDemoAccountOnlyWhenEnabled() {
		User demo = new User(DemoAccount.EMAIL, "unusable", DemoAccount.FULL_NAME);
		when(userRepository.findByEmail(DemoAccount.EMAIL)).thenReturn(Optional.of(demo));
		when(jwtService.generateToken(demo)).thenReturn("demo-token");

		assertThat(authService.loginAsDemo().accessToken()).isEqualTo("demo-token");

		AuthService withoutDemo = new AuthService(userRepository, passwordEncoder, jwtService,
				new AccessProperties(true, false));
		assertThatThrownBy(withoutDemo::loginAsDemo).isInstanceOf(AccessDeniedExceptions.DemoUnavailableException.class);
	}

}
