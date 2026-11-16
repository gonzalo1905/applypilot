import { HttpErrorResponse } from '@angular/common/http';

/** The error JSON every backend endpoint returns (see the backend's `ApiError` record). */
export interface ApiError {
  timestamp: string;
  status: number;
  error: string;
  code: string;
  message: string;
  path: string;
  fieldErrors: ApiFieldError[];
}

export interface ApiFieldError {
  field: string;
  message: string;
}

function isApiError(value: unknown): value is ApiError {
  return (
    typeof value === 'object' &&
    value !== null &&
    typeof (value as ApiError).code === 'string' &&
    typeof (value as ApiError).message === 'string'
  );
}

/** Turns any failed HTTP call into a message that is safe to show to the user. */
export function toErrorMessage(error: unknown): string {
  if (error instanceof HttpErrorResponse) {
    if (error.status === 0) {
      return 'Cannot reach the server. Check that the backend is running and try again.';
    }
    if (isApiError(error.error)) {
      return error.error.message;
    }
  }
  return 'Something went wrong. Please try again.';
}

/** Extracts per-field validation messages, keyed by field name. */
export function toFieldErrors(error: unknown): Record<string, string> {
  if (error instanceof HttpErrorResponse && isApiError(error.error)) {
    const entries = (error.error.fieldErrors ?? []).map((fieldError) => [
      fieldError.field,
      fieldError.message,
    ]);
    return Object.fromEntries(entries) as Record<string, string>;
  }
  return {};
}
