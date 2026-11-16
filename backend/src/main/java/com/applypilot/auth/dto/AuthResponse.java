package com.applypilot.auth.dto;

import com.applypilot.user.dto.UserResponse;

/**
 * Returned by register and login.
 *
 * @param expiresIn token lifetime in seconds
 */
public record AuthResponse(String accessToken, String tokenType, long expiresIn, UserResponse user) {

	public static AuthResponse bearer(String accessToken, long expiresIn, UserResponse user) {
		return new AuthResponse(accessToken, "Bearer", expiresIn, user);
	}

}
