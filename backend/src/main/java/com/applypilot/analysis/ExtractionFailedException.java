package com.applypilot.analysis;

/**
 * The language model could not produce a valid extraction. Carries the reason, which becomes
 * the {@link FallbackReason} stored with the keyword-based result.
 */
public class ExtractionFailedException extends RuntimeException {

	private final FallbackReason reason;

	public ExtractionFailedException(FallbackReason reason, String message, Throwable cause) {
		super(message, cause);
		this.reason = reason;
	}

	public FallbackReason getReason() {
		return reason;
	}

}
