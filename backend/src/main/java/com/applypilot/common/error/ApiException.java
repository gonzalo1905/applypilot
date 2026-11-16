package com.applypilot.common.error;

import org.springframework.http.HttpStatus;

/**
 * Base class for expected, business-level failures. Each subclass picks the HTTP status and
 * a stable error code; {@link GlobalExceptionHandler} turns it into an {@link ApiError}.
 */
public abstract class ApiException extends RuntimeException {

	private final HttpStatus status;

	private final String code;

	protected ApiException(HttpStatus status, String code, String message) {
		super(message);
		this.status = status;
		this.code = code;
	}

	public HttpStatus getStatus() {
		return status;
	}

	public String getCode() {
		return code;
	}

}
