import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { User } from '../auth.models';
import { AuthService } from '../auth.service';
import { Register } from './register';

const user: User = {
  id: '0b0e7c1e-0000-4000-8000-000000000001',
  email: 'ada@example.com',
  fullName: 'Ada Lovelace',
  createdAt: '2026-01-01T00:00:00Z',
};

describe('Register', () => {
  let fixture: ComponentFixture<Register>;
  let page: HTMLElement;
  let auth: { register: ReturnType<typeof vi.fn> };
  let navigateByUrl: ReturnType<typeof vi.spyOn>;

  beforeEach(async () => {
    auth = { register: vi.fn(() => of(user)) };
    await TestBed.configureTestingModule({
      imports: [Register],
      providers: [provideRouter([]), { provide: AuthService, useValue: auth }],
    }).compileComponents();
    navigateByUrl = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    fixture = TestBed.createComponent(Register);
    page = fixture.nativeElement as HTMLElement;
    await fixture.whenStable();
  });

  function type(selector: string, value: string): void {
    const input = page.querySelector<HTMLInputElement>(selector)!;
    input.value = value;
    input.dispatchEvent(new Event('input'));
  }

  function fillValidForm(): void {
    type('#fullName', 'Ada Lovelace');
    type('#email', 'ada@example.com');
    type('#password', 's3cret-password');
  }

  async function submit(): Promise<void> {
    page.querySelector('form')!.dispatchEvent(new Event('submit'));
    await fixture.whenStable();
  }

  function fieldErrors(): (string | undefined)[] {
    return Array.from(page.querySelectorAll('.field-error')).map((e) => e.textContent?.trim());
  }

  it('does not call the API and shows an error per field when the form is empty', async () => {
    await submit();

    expect(auth.register).not.toHaveBeenCalled();
    expect(fieldErrors()).toEqual([
      'Enter your name (up to 120 characters).',
      'Enter a valid email address.',
      'Use between 8 and 72 characters.',
    ]);
  });

  it('rejects a password shorter than 8 characters', async () => {
    fillValidForm();
    type('#password', 'short');

    await submit();

    expect(auth.register).not.toHaveBeenCalled();
    expect(fieldErrors()).toEqual(['Use between 8 and 72 characters.']);
  });

  it('creates the account and goes to the board', async () => {
    fillValidForm();

    await submit();

    expect(auth.register).toHaveBeenCalledWith({
      fullName: 'Ada Lovelace',
      email: 'ada@example.com',
      password: 's3cret-password',
    });
    expect(navigateByUrl).toHaveBeenCalledWith('/board');
  });

  it('shows the server message when the email is already registered', async () => {
    auth.register.mockReturnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 409,
            error: {
              code: 'EMAIL_ALREADY_USED',
              message: 'An account with this email already exists',
              fieldErrors: [],
            },
          }),
      ),
    );
    fillValidForm();

    await submit();

    expect(page.querySelector('[role="alert"]')?.textContent).toBe(
      'An account with this email already exists',
    );
    expect(page.querySelector<HTMLButtonElement>('button[type="submit"]')?.disabled).toBe(false);
    expect(navigateByUrl).not.toHaveBeenCalled();
  });

  it('shows validation messages returned by the server next to their fields', async () => {
    auth.register.mockReturnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 400,
            error: {
              code: 'VALIDATION_FAILED',
              message: 'Request validation failed',
              fieldErrors: [{ field: 'email', message: 'must be a well-formed email address' }],
            },
          }),
      ),
    );
    fillValidForm();

    await submit();

    expect(fieldErrors()).toEqual(['Email must be a well-formed email address.']);
  });
});
