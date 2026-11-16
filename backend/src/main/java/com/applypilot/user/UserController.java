package com.applypilot.user;

import com.applypilot.user.dto.UserResponse;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {

	private final UserService userService;

	public UserController(UserService userService) {
		this.userService = userService;
	}

	/**
	 * Returns the caller's own profile. The user id comes from the verified token's subject,
	 * never from a request parameter, so one user cannot ask for another user's data.
	 */
	@GetMapping("/me")
	public UserResponse me(@AuthenticationPrincipal Jwt jwt) {
		return userService.getById(UUID.fromString(jwt.getSubject()));
	}

}
