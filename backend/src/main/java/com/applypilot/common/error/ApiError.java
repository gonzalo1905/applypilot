package com.applypilot.common.error;

import java.time.Instant;
import java.util.List;

/**
 * The single JSON shape returned for every error, so clients can handle failures uniformly.
 *
 * @param code machine-readable error code (for example {@code VALIDATION_FAILED})
 * @param fieldErrors per-field validation problems; empty when the error is not field-specific
 */
public record ApiError(
		Instant timestamp,
		int status,
		String error,
		String code,
		String message,
		String path,
		List<FieldError> fieldErrors) {

	public record FieldError(String field, String message) {
	}

}
