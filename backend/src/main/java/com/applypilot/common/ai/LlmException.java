package com.applypilot.common.ai;

/**
 * A call to the language model failed. Not an {@code ApiException}: callers are expected to
 * catch it and degrade (for example to a non-AI fallback) instead of failing the request.
 */
public abstract class LlmException extends RuntimeException {

	protected LlmException(String message, Throwable cause) {
		super(message, cause);
	}

}
