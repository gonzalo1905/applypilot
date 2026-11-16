package com.applypilot.auth;

import com.applypilot.auth.dto.AuthResponse;
import com.applypilot.auth.dto.LoginRequest;
import com.applypilot.auth.dto.RegisterRequest;
import com.applypilot.common.error.InvalidRequestException;
import com.applypilot.user.User;
import com.applypilot.user.UserRepository;
import com.applypilot.user.dto.UserResponse;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

	/** BCrypt only reads the first 72 bytes of a password, so longer ones are rejected. */
	private static final int BCRYPT_MAX_BYTES = 72;

	private final UserRepository userRepository;

	private final PasswordEncoder passwordEncoder;

	private final JwtService jwtService;

	public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.jwtService = jwtService;
	}

	@Transactional
	public AuthResponse register(RegisterRequest request) {
		String email = normalizeEmail(request.email());
		if (exceedsBcryptLimit(request.password())) {
			throw new InvalidRequestException("Password must be at most " + BCRYPT_MAX_BYTES + " bytes long");
		}
		if (userRepository.existsByEmail(email)) {
			throw new EmailAlreadyUsedException();
		}
		User user = new User(email, passwordEncoder.encode(request.password()), request.fullName().trim());
		try {
			user = userRepository.saveAndFlush(user);
		}
		catch (DataIntegrityViolationException ex) {
			// Two registrations with the same email raced past the check above; the unique
			// constraint in the database is the real guarantee.
			throw new EmailAlreadyUsedException();
		}
		return toAuthResponse(user);
	}

	@Transactional(readOnly = true)
	public AuthResponse login(LoginRequest request) {
		User user = userRepository.findByEmail(normalizeEmail(request.email()))
			.filter(candidate -> !exceedsBcryptLimit(request.password())
					&& passwordEncoder.matches(request.password(), candidate.getPasswordHash()))
			.orElseThrow(InvalidCredentialsException::new);
		return toAuthResponse(user);
	}

	private AuthResponse toAuthResponse(User user) {
		return AuthResponse.bearer(jwtService.generateToken(user), jwtService.expiresInSeconds(),
				UserResponse.from(user));
	}

	private static String normalizeEmail(String email) {
		return email.trim().toLowerCase(Locale.ROOT);
	}

	private static boolean exceedsBcryptLimit(String password) {
		return password.getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_BYTES;
	}

}
