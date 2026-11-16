import { DatePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { Observable } from 'rxjs';
import { AuthService } from '../auth/auth.service';
import { toErrorMessage } from '../core/api-error';
import { Topbar } from '../core/topbar/topbar';
import { CvApi, CvMetadata } from './cv.api';

/** Must match the backend limit (app.cv.max-size). The backend enforces it; this is fast feedback. */
export const MAX_CV_SIZE_BYTES = 5 * 1024 * 1024;

@Component({
  selector: 'app-profile',
  imports: [DatePipe, Topbar],
  templateUrl: './profile.html',
  styleUrl: './profile.scss',
})
export class Profile {
  private readonly auth = inject(AuthService);
  private readonly cvApi = inject(CvApi);

  protected readonly user = this.auth.user;
  protected readonly cv = signal<CvMetadata | null>(null);
  protected readonly loading = signal(true);
  /** True while an upload, download or delete is in flight. */
  protected readonly busy = signal(false);
  protected readonly confirmingDelete = signal(false);
  protected readonly errorMessage = signal<string | null>(null);

  constructor() {
    this.cvApi.get().subscribe({
      next: (cv) => {
        this.cv.set(cv);
        this.loading.set(false);
      },
      error: (error: unknown) => {
        this.errorMessage.set(toErrorMessage(error));
        this.loading.set(false);
      },
    });
  }

  protected onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    // Reset the input so choosing the same file again still fires "change".
    input.value = '';
    if (file === undefined) {
      return;
    }
    if (file.type !== 'application/pdf') {
      this.errorMessage.set('Only PDF files are accepted.');
      return;
    }
    if (file.size > MAX_CV_SIZE_BYTES) {
      this.errorMessage.set('The file is larger than 5 MB.');
      return;
    }
    this.run(this.cvApi.upload(file), (cv) => this.cv.set(cv));
  }

  protected download(): void {
    const fileName = this.cv()?.fileName ?? 'cv.pdf';
    this.run(this.cvApi.download(), (blob) => saveBlob(blob, fileName));
  }

  protected askToDelete(): void {
    this.confirmingDelete.set(true);
  }

  protected cancelDelete(): void {
    this.confirmingDelete.set(false);
  }

  protected confirmDelete(): void {
    this.confirmingDelete.set(false);
    this.run(this.cvApi.remove(), () => this.cv.set(null));
  }

  protected formatSize(bytes: number): string {
    return bytes < 1024 * 1024
      ? `${Math.max(1, Math.round(bytes / 1024))} KB`
      : `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
  }

  /** Runs one CV request with the shared busy/error handling. */
  private run<T>(request: Observable<T>, onSuccess: (value: T) => void): void {
    this.busy.set(true);
    this.errorMessage.set(null);
    request.subscribe({
      next: (value) => {
        onSuccess(value);
        this.busy.set(false);
      },
      error: (error: unknown) => {
        this.errorMessage.set(toErrorMessage(error));
        this.busy.set(false);
      },
    });
  }
}

/** Hands a downloaded file to the browser's "save" flow. */
function saveBlob(blob: Blob, fileName: string): void {
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = fileName;
  link.click();
  URL.revokeObjectURL(url);
}
