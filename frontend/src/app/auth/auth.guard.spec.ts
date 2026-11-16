import { TestBed } from '@angular/core/testing';
import {
  ActivatedRouteSnapshot,
  Router,
  RouterStateSnapshot,
  UrlTree,
  provideRouter,
} from '@angular/router';
import { authGuard, guestGuard } from './auth.guard';
import { AuthService } from './auth.service';

function runGuards(token: string | null): { auth: unknown; guest: unknown; router: Router } {
  TestBed.configureTestingModule({
    providers: [provideRouter([]), { provide: AuthService, useValue: { token: () => token } }],
  });
  const route = {} as ActivatedRouteSnapshot;
  const state = { url: '/board' } as RouterStateSnapshot;
  return {
    auth: TestBed.runInInjectionContext(() => authGuard(route, state)),
    guest: TestBed.runInInjectionContext(() => guestGuard(route, state)),
    router: TestBed.inject(Router),
  };
}

describe('route guards', () => {
  it('lets a logged-in user into private pages and away from the auth pages', () => {
    const { auth, guest, router } = runGuards('jwt-token');

    expect(auth).toBe(true);
    expect(guest).toBeInstanceOf(UrlTree);
    expect(router.serializeUrl(guest as UrlTree)).toBe('/board');
  });

  it('sends an anonymous user to login, remembering where they wanted to go', () => {
    const { auth, guest, router } = runGuards(null);

    expect(auth).toBeInstanceOf(UrlTree);
    expect(router.serializeUrl(auth as UrlTree)).toBe('/login?returnUrl=%2Fboard');
    expect(guest).toBe(true);
  });
});
