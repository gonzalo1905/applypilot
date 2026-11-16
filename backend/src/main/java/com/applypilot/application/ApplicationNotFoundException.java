package com.applypilot.application;

import com.applypilot.common.error.ApiException;
import org.springframework.http.HttpStatus;

/**
 * Thrown both when the id does not exist and when it belongs to another user. Answering 404
 * in both cases (instead of 403 for the second) does not reveal that the id exists.
 */
public class ApplicationNotFoundException extends ApiException {

	public ApplicationNotFoundException() {
		super(HttpStatus.NOT_FOUND, "APPLICATION_NOT_FOUND", "Application not found");
	}

}
