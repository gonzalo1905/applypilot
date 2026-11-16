import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { environment } from '../../environments/environment';
import { AuthResponse } from './auth.models';
import { AuthService } from './auth.service';

const STORAGE_KEY = 'applypilot.session';

const authResponse: AuthResponse = {
  accessToken: 'jwt-token',
  tokenType: 'Bearer',
  expiresIn: 3600,
  user: {
    id: '0b0e7c1e-0000-4000-8000-000000000001',
    email: 'ada@example.com',
    fullName: 'Ada Lovelace',
    createdAt: '2026-01-01T00:00:00Z',
  },
};

function setup(): { auth: AuthService; http: HttpTestingController } {
  TestBed.configureTestingModule({
    providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
  });
  return { auth: TestBed.inject(AuthService), http: TestBed.inject(HttpTestingController) };
}

describe('AuthService', () => {
  beforeEach(() => localStorage.clear());

  it('starts without a session', () => {
    const { auth } = setup();

    expect(auth.isAuthenticated()).toBe(false);
    expect(auth.token()).toBeNull();
    expect(auth.user()).toBeNull();
  });

  it('stores the token and the user after a successful login', () => {
    const { auth, http } = setup();

    auth.login({ email: 'ada@example.com', password: 's3cret-password' }).subscribe();
    const request = http.expectOne(`${environment.apiUrl}/auth/login`);
    expect(request.request.method).toBe('POST');
    request.flush(authResponse);

    expect(auth.token()).toBe('jwt-token');
    expect(auth.user()?.fullName).toBe('Ada Lovelace');
    expect(localStorage.getItem(STORAGE_KEY)).toContain('jwt-token');
    http.verify();
  });

  it('restores a stored session after a page reload', () => {
    localStorage.setItem(
      STORAGE_KEY,
      JSON.stringify({ token: 'stored', expiresAt: Date.now() + 60_000, user: authResponse.user }),
    );

    const { auth } = setup();

    expect(auth.isAuthenticated()).toBe(true);
    expect(auth.token()).toBe('stored');
  });

  it('ignores a stored session whose token has expired', () => {
    localStorage.setItem(
      STORAGE_KEY,
      JSON.stringify({ token: 'old', expiresAt: Date.now() - 1, user: authResponse.user }),
    );

    const { auth } = setup();

    expect(auth.isAuthenticated()).toBe(false);
    expect(auth.token()).toBeNull();
  });

  it('clears the session on logout', () => {
    const { auth, http } = setup();
    auth.login({ email: 'ada@example.com', password: 's3cret-password' }).subscribe();
    http.expectOne(`${environment.apiUrl}/auth/login`).flush(authResponse);
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);

    auth.logout();

    expect(auth.isAuthenticated()).toBe(false);
    expect(localStorage.getItem(STORAGE_KEY)).toBeNull();
    expect(navigate).toHaveBeenCalledWith(['/login']);
  });
});
