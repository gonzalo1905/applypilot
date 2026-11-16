package com.applypilot.cv;

import com.applypilot.common.error.ApiException;
import org.springframework.http.HttpStatus;

public class CvNotFoundException extends ApiException {

	public CvNotFoundException() {
		super(HttpStatus.NOT_FOUND, "CV_NOT_FOUND", "You have not uploaded a CV yet");
	}

}
