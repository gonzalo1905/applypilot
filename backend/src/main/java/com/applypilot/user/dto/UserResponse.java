package com.applypilot.user.dto;

import com.applypilot.user.User;
import java.time.Instant;
import java.util.UUID;

/**
 * Public view of a user. The entity never leaves the service layer, so the password hash
 * cannot leak through the API by accident.
 */
public record UserResponse(UUID id, String email, String fullName, Instant createdAt) {

	public static UserResponse from(User user) {
		return new UserResponse(user.getId(), user.getEmail(), user.getFullName(), user.getCreatedAt());
	}

}
