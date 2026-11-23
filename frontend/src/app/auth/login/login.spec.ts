import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { User } from '../auth.models';
import { AuthService } from '../auth.service';
import { Login } from './login';

const user: User = {
  id: '0b0e7c1e-0000-4000-8000-000000000001',
  email: 'ada@example.com',
  fullName: 'Ada Lovelace',
  createdAt: '2026-01-01T00:00:00Z',
};

describe('Login', () => {
  let fixture: ComponentFixture<Login>;
  let page: HTMLElement;
  let auth: { login: ReturnType<typeof vi.fn> };
  let navigateByUrl: ReturnType<typeof vi.spyOn>;

  async function render(returnUrl: string | null = null): Promise<void> {
    auth = { login: vi.fn(() => of(user)) };
    await TestBed.configureTestingModule({
      imports: [Login],
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: auth },
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: { queryParamMap: convertToParamMap(returnUrl ? { returnUrl } : {}) },
          },
        },
      ],
    }).compileComponents();
    navigateByUrl = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    fixture = TestBed.createComponent(Login);
    page = fixture.nativeElement as HTMLElement;
    await fixture.whenStable();
  }

  function type(selector: string, value: string): void {
    const input = page.querySelector<HTMLInputElement>(selector)!;
    input.value = value;
    input.dispatchEvent(new Event('input'));
  }

  async function submit(): Promise<void> {
    page.querySelector('form')!.dispatchEvent(new Event('submit'));
    await fixture.whenStable();
  }

  it('does not call the API and shows field errors when the form is empty', async () => {
    await render();

    await submit();

    expect(auth.login).not.toHaveBeenCalled();
    const errors = Array.from(page.querySelectorAll('.field-error')).map((e) =>
      e.textContent?.trim(),
    );
    expect(errors).toEqual(['Enter a valid email address.', 'Enter your password.']);
  });

  it('rejects a malformed email without calling the API', async () => {
    await render();
    type('#email', 'not-an-email');
    type('#password', 's3cret-password');

    await submit();

    expect(auth.login).not.toHaveBeenCalled();
    expect(page.querySelector('.field-error')?.textContent).toContain('valid email');
  });

  it('logs in with the typed credentials and goes to the board', async () => {
    await render();
    type('#email', 'ada@example.com');
    type('#password', 's3cret-password');

    await submit();

    expect(auth.login).toHaveBeenCalledWith({
      email: 'ada@example.com',
      password: 's3cret-password',
    });
    expect(navigateByUrl).toHaveBeenCalledWith('/board');
  });

  it('returns to the page the user originally asked for', async () => {
    await render('/profile');
    type('#email', 'ada@example.com');
    type('#password', 's3cret-password');

    await submit();

    expect(navigateByUrl).toHaveBeenCalledWith('/profile');
  });

  it.each(['https://evil.example/phish', '//evil.example', 'javascript:alert(1)'])(
    'ignores the external return URL %s',
    async (returnUrl) => {
      await render(returnUrl);
      type('#email', 'ada@example.com');
      type('#password', 's3cret-password');

      await submit();

      expect(navigateByUrl).toHaveBeenCalledWith('/board');
    },
  );

  it('shows the server message and lets the user try again when login fails', async () => {
    await render();
    auth.login.mockReturnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 401,
            error: {
              code: 'INVALID_CREDENTIALS',
              message: 'Invalid email or password',
              fieldErrors: [],
            },
          }),
      ),
    );
    type('#email', 'ada@example.com');
    type('#password', 'wrong-password');

    await submit();

    expect(page.querySelector('[role="alert"]')?.textContent).toBe('Invalid email or password');
    expect(page.querySelector<HTMLButtonElement>('button[type="submit"]')?.disabled).toBe(false);
    expect(navigateByUrl).not.toHaveBeenCalled();
  });

  it('explains when the server cannot be reached', async () => {
    await render();
    auth.login.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 0 })));
    type('#email', 'ada@example.com');
    type('#password', 's3cret-password');

    await submit();

    expect(page.querySelector('[role="alert"]')?.textContent).toContain('Cannot reach the server');
  });
});
