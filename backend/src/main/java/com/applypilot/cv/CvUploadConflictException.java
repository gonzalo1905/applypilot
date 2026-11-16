package com.applypilot.cv;

import com.applypilot.common.error.ApiException;
import org.springframework.http.HttpStatus;

/**
 * Two first uploads for the same user arrived at the same time and this one lost the race
 * (the database allows only one CV per user).
 */
public class CvUploadConflictException extends ApiException {

	public CvUploadConflictException() {
		super(HttpStatus.CONFLICT, "CV_UPLOAD_CONFLICT", "Another upload of your CV is in progress. Try again.");
	}

}
