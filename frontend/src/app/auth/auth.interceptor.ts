import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { environment } from '../../environments/environment';
import { AuthService } from './auth.service';

/**
 * Adds "Authorization: Bearer <jwt>" to requests that go to our API, and logs the user out
 * when the API answers 401 (the token expired or is no longer valid).
 *
 * The token is never attached to other hosts, and not to the login/register endpoints,
 * which are public and must keep working even if a stale token is still stored.
 */
export const authInterceptor: HttpInterceptorFn = (request, next) => {
  const auth = inject(AuthService);
  const isApiCall = request.url.startsWith(environment.apiUrl);
  const isPublicAuthCall = request.url.startsWith(`${environment.apiUrl}/auth/`);

  if (!isApiCall || isPublicAuthCall) {
    return next(request);
  }

  const token = auth.token();
  const authorized =
    token === null ? request : request.clone({ setHeaders: { Authorization: `Bearer ${token}` } });

  return next(authorized).pipe(
    catchError((error: unknown) => {
      if (error instanceof HttpErrorResponse && error.status === 401) {
        auth.logout();
      }
      return throwError(() => error);
    }),
  );
};
