import { Component, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { toErrorMessage } from '../../core/api-error';
import { AuthService } from '../auth.service';

@Component({
  selector: 'app-login',
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './login.html',
})
export class Login {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly formBuilder = inject(NonNullableFormBuilder);

  protected readonly form = this.formBuilder.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required]],
  });
  protected readonly submitting = signal(false);
  protected readonly errorMessage = signal<string | null>(null);

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitting.set(true);
    this.errorMessage.set(null);

    this.auth.login(this.form.getRawValue()).subscribe({
      next: () => void this.router.navigateByUrl(this.returnUrl()),
      error: (error: unknown) => {
        this.errorMessage.set(toErrorMessage(error));
        this.submitting.set(false);
      },
    });
  }

  protected tryDemo(): void {
    this.submitting.set(true);
    this.errorMessage.set(null);
    this.auth.loginAsDemo().subscribe({
      next: () => void this.router.navigateByUrl('/board'),
      error: (error: unknown) => {
        this.errorMessage.set(toErrorMessage(error));
        this.submitting.set(false);
      },
    });
  }

  /** Where to go after login. Only in-app paths are accepted, to avoid open redirects. */
  private returnUrl(): string {
    const requested = this.route.snapshot.queryParamMap.get('returnUrl');
    const isInternalPath =
      requested !== null && requested.startsWith('/') && !requested.startsWith('//');
    return isInternalPath ? requested : '/board';
  }
}
