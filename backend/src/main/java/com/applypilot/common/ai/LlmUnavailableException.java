package com.applypilot.common.ai;

/** The provider could not be reached or answered with an error (timeout, 5xx, bad key...). */
public class LlmUnavailableException extends LlmException {

	public LlmUnavailableException(String message, Throwable cause) {
		super(message, cause);
	}

}
