package com.applypilot.auth;

import com.applypilot.common.error.ApiException;
import org.springframework.http.HttpStatus;

public class EmailAlreadyUsedException extends ApiException {

	public EmailAlreadyUsedException() {
		super(HttpStatus.CONFLICT, "EMAIL_ALREADY_USED", "An account with this email already exists");
	}

}
