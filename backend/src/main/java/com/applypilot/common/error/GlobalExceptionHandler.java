package com.applypilot.common.error;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Translates every exception into the same {@link ApiError} JSON shape.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(MethodArgumentNotValidException.class)
	ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
		List<ApiError.FieldError> fieldErrors = ex.getBindingResult()
			.getFieldErrors()
			.stream()
			.map(error -> new ApiError.FieldError(error.getField(), error.getDefaultMessage()))
			.toList();
		return build(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Request validation failed", request, fieldErrors);
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	ResponseEntity<ApiError> handleUnreadableBody(HttpMessageNotReadableException ex, HttpServletRequest request) {
		return build(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "Request body is missing or malformed", request,
				List.of());
	}

	@ExceptionHandler(ApiException.class)
	ResponseEntity<ApiError> handleApiException(ApiException ex, HttpServletRequest request) {
		return build(ex.getStatus(), ex.getCode(), ex.getMessage(), request, List.of());
	}

	@ExceptionHandler(Exception.class)
	ResponseEntity<ApiError> handleOther(Exception ex, HttpServletRequest request) {
		// Spring MVC's own exceptions (404, 405, 415, ...) already know their HTTP status.
		if (ex instanceof ErrorResponse errorResponse) {
			HttpStatus status = HttpStatus.valueOf(errorResponse.getStatusCode().value());
			return build(status, status.name(), status.getReasonPhrase(), request, List.of());
		}
		// Anything else is a bug: log the details, never leak them to the client.
		log.error("Unhandled exception on {} {}", request.getMethod(), request.getRequestURI(), ex);
		return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "An unexpected error occurred", request,
				List.of());
	}

	private ResponseEntity<ApiError> build(HttpStatus status, String code, String message, HttpServletRequest request,
			List<ApiError.FieldError> fieldErrors) {
		ApiError body = new ApiError(Instant.now(), status.value(), status.getReasonPhrase(), code, message,
				request.getRequestURI(), fieldErrors);
		return ResponseEntity.status(status).body(body);
	}

}
