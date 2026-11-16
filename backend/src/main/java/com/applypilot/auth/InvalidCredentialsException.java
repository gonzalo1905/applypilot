package com.applypilot.auth;

import com.applypilot.common.error.ApiException;
import org.springframework.http.HttpStatus;

/**
 * Thrown for both an unknown email and a wrong password, with the same message, so the
 * login endpoint does not reveal which emails are registered.
 */
public class InvalidCredentialsException extends ApiException {

	public InvalidCredentialsException() {
		super(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Invalid email or password");
	}

}
