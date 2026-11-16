import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, map, tap } from 'rxjs';
import { environment } from '../../environments/environment';
import { AuthResponse, LoginRequest, RegisterRequest, User } from './auth.models';

interface Session {
  token: string;
  /** Epoch milliseconds after which the token is no longer valid. */
  expiresAt: number;
  user: User;
}

const STORAGE_KEY = 'applypilot.session';

/**
 * Owns the logged-in session: talks to the auth endpoints, keeps the JWT and the user in a
 * signal, and mirrors them to localStorage so a page reload does not log the user out.
 *
 * Trade-off: localStorage is readable by any script on the page, so an XSS bug could steal
 * the token. An httpOnly cookie would avoid that but needs CSRF protection on the backend.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly session = signal<Session | null>(readStoredSession());

  readonly user = computed(() => this.session()?.user ?? null);
  readonly isAuthenticated = computed(() => this.session() !== null);

  /** Returns the JWT, or null when there is no session or the token has expired. */
  token(): string | null {
    const session = this.session();
    if (session === null) {
      return null;
    }
    if (session.expiresAt <= Date.now()) {
      this.clearSession();
      return null;
    }
    return session.token;
  }

  login(request: LoginRequest): Observable<User> {
    return this.http
      .post<AuthResponse>(`${environment.apiUrl}/auth/login`, request)
      .pipe(map((response) => this.startSession(response)));
  }

  register(request: RegisterRequest): Observable<User> {
    return this.http
      .post<AuthResponse>(`${environment.apiUrl}/auth/register`, request)
      .pipe(map((response) => this.startSession(response)));
  }

  /** Re-reads the profile from the API so the UI never trusts a stale copy from storage. */
  refreshUser(): Observable<User> {
    return this.http.get<User>(`${environment.apiUrl}/users/me`).pipe(
      tap((user) => {
        const session = this.session();
        if (session !== null) {
          this.storeSession({ ...session, user });
        }
      }),
    );
  }

  logout(): void {
    this.clearSession();
    void this.router.navigate(['/login']);
  }

  private startSession(response: AuthResponse): User {
    this.storeSession({
      token: response.accessToken,
      expiresAt: Date.now() + response.expiresIn * 1000,
      user: response.user,
    });
    return response.user;
  }

  private storeSession(session: Session): void {
    this.session.set(session);
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(session));
    } catch {
      // Storage can be unavailable (private mode, quota). The session then lives in memory only.
    }
  }

  private clearSession(): void {
    this.session.set(null);
    try {
      localStorage.removeItem(STORAGE_KEY);
    } catch {
      // Nothing to clean up if storage is unavailable.
    }
  }
}

function readStoredSession(): Session | null {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (raw === null) {
      return null;
    }
    const session = JSON.parse(raw) as Partial<Session>;
    const isValid =
      typeof session.token === 'string' &&
      typeof session.expiresAt === 'number' &&
      session.expiresAt > Date.now() &&
      typeof session.user?.fullName === 'string';
    return isValid ? (session as Session) : null;
  } catch {
    return null;
  }
}
