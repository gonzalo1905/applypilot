package com.applypilot.auth;

import com.applypilot.common.error.ApiException;
import org.springframework.http.HttpStatus;

/** Errors for access that this deployment does not offer. */
public final class AccessDeniedExceptions {

	private AccessDeniedExceptions() {
	}

	public static class RegistrationClosedException extends ApiException {

		public RegistrationClosedException() {
			super(HttpStatus.FORBIDDEN, "REGISTRATION_CLOSED",
					"Registration is closed on this demo. Use \"Try the demo\" to explore ApplyPilot.");
		}

	}

	public static class DemoUnavailableException extends ApiException {

		public DemoUnavailableException() {
			super(HttpStatus.NOT_FOUND, "DEMO_UNAVAILABLE", "There is no demo account on this server");
		}

	}

}
