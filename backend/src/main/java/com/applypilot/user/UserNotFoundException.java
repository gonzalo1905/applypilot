package com.applypilot.user;

import com.applypilot.common.error.ApiException;
import org.springframework.http.HttpStatus;

/**
 * The token is valid but its account no longer exists (it was deleted after the token was
 * issued). Answered with 401, not 404: the session is no longer good, and clients react to
 * 401 by logging the user out.
 */
public class UserNotFoundException extends ApiException {

	public UserNotFoundException() {
		super(HttpStatus.UNAUTHORIZED, "ACCOUNT_NOT_FOUND", "This account no longer exists");
	}

}
