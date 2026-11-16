package com.applypilot.common.error;

import org.springframework.http.HttpStatus;

/**
 * A request that is well-formed but breaks a rule Bean Validation annotations cannot express.
 */
public class InvalidRequestException extends ApiException {

	public InvalidRequestException(String message) {
		super(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", message);
	}

}
