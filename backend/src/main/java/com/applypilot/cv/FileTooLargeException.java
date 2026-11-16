package com.applypilot.cv;

import com.applypilot.common.error.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.util.unit.DataSize;

public class FileTooLargeException extends ApiException {

	public FileTooLargeException(DataSize maxSize) {
		super(HttpStatus.valueOf(413), "FILE_TOO_LARGE", "The file is larger than " + maxSize.toMegabytes() + " MB");
	}

}
