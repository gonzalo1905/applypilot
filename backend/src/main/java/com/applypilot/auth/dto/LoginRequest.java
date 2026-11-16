package com.applypilot.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(@NotBlank String email, @NotBlank String password) {

	/** Keeps the password out of logs if the request is ever printed. */
	@Override
	public String toString() {
		return "LoginRequest[email=" + email + ", password=***]";
	}

}
