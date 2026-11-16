import { Component, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { toErrorMessage, toFieldErrors } from '../../core/api-error';
import { AuthService } from '../auth.service';

@Component({
  selector: 'app-register',
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './register.html',
})
export class Register {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly formBuilder = inject(NonNullableFormBuilder);

  // These rules mirror the backend's RegisterRequest validation. The backend stays the
  // source of truth; validating here too just gives faster feedback.
  protected readonly form = this.formBuilder.group({
    fullName: ['', [Validators.required, Validators.maxLength(120)]],
    email: ['', [Validators.required, Validators.email, Validators.maxLength(255)]],
    password: ['', [Validators.required, Validators.minLength(8), Validators.maxLength(72)]],
  });
  protected readonly submitting = signal(false);
  protected readonly errorMessage = signal<string | null>(null);
  /** Validation messages returned by the backend, keyed by field name. */
  protected readonly serverErrors = signal<Record<string, string>>({});

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitting.set(true);
    this.errorMessage.set(null);
    this.serverErrors.set({});

    this.auth.register(this.form.getRawValue()).subscribe({
      next: () => void this.router.navigateByUrl('/board'),
      error: (error: unknown) => {
        this.errorMessage.set(toErrorMessage(error));
        this.serverErrors.set(toFieldErrors(error));
        this.submitting.set(false);
      },
    });
  }
}
